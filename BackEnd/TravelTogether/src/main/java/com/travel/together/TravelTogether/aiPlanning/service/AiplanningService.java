package com.travel.together.TravelTogether.aiPlanning.service;

import com.travel.together.TravelTogether.aiPlanning.dto.*;
import com.travel.together.TravelTogether.album.entity.PhotoAlbum;
import com.travel.together.TravelTogether.album.repository.PhotoAlbumRepository;
import com.travel.together.TravelTogether.auth.entity.User;
import com.travel.together.TravelTogether.auth.repository.UserRepository;
import com.travel.together.TravelTogether.trip.entity.Day;
import com.travel.together.TravelTogether.trip.entity.Schedule;
import com.travel.together.TravelTogether.trip.entity.Trip;
import com.travel.together.TravelTogether.trip.entity.TripMember;
import com.travel.together.TravelTogether.trip.repository.DayRepository;
import com.travel.together.TravelTogether.trip.repository.ScheduleRepository;
import com.travel.together.TravelTogether.trip.repository.TripMemberRepository;
import com.travel.together.TravelTogether.trip.repository.TripRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiplanningService {
    private final ScheduleRepository scheduleRepository;
    private final KakaoService kakaoService;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final DayRepository dayRepository;
    private final UserRepository userRepository;
    private final PhotoAlbumRepository photoAlbumRepository;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional
    public Integer savePlanningData(OpenaiResponseDto responseDto) {
        if (responseDto == null || responseDto.getSchedule() == null) {
            System.out.println("ResponseDto or schedule is null. Skipping processing.");
            return -1;
        }

        // 현재 사용자 정보 가져오기
        User user = getCurrentUser(); // 현재 사용자 정보 가져오기

        // Trip 생성 및 저장
        Trip trip = createTrip(responseDto, user);
        tripRepository.save(trip);

        // 앨범 생성
        PhotoAlbum album = new PhotoAlbum();
        album.setTrip(trip);
        album.setImageUrl("default.jpg");  // 기본 앨범 커버 이미지 설정
        photoAlbumRepository.save(album);

        // TripMember 생성 및 저장
        TripMember tripMember = new TripMember();
        tripMember.setTrip(trip);
        tripMember.setUser(user);
        tripMember.setIsOwner(true);
        tripMemberRepository.save(tripMember);

        List<OpenaiResponseDto.DateActivities> days = responseDto.getSchedule().getDays();
        if (days == null || days.isEmpty()) {
            System.out.println("No days found in schedule. Skipping processing.");
            return -1;
        }

        try {
            for (OpenaiResponseDto.DateActivities dayDto : days) {
                LocalDateTime startTime = parseDate(dayDto.getDate());
                Day day = new Day();
                day.setTrip(trip); // Trip 객체 설정
                day.setStartTime(startTime);
                day.setOrderNum(1);

                dayRepository.save(day);

                List<OpenaiResponseDto.Activity> activities = dayDto.getActivities();
                if (activities == null || activities.isEmpty()) {
                    System.out.println("No activities found for date: " + dayDto.getDate());
                    continue;
                }

                for (OpenaiResponseDto.Activity activity : activities) {
                    processActivity(activity, day); // Activity와 Day 객체를 함께 전달
                }
            }
        } catch (Exception e) {
            System.out.println("Error saving planning data: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save planning data", e);
        }
        return trip.getId();
    }

    // Trip 테이블 DB 입력 코드
    private Trip createTrip(OpenaiResponseDto responseDto, User user) {
        LocalDateTime startDate = parseDate(responseDto.getStartDate());
        LocalDateTime endDate = parseDate(responseDto.getEndDate());

        return Trip.builder()
                .creator(user)
                .title(responseDto.getTitle())
                .startDate(startDate)
                .endDate(endDate)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // YYYYMMDD 형식으로 출력하기 위한 parsing 코드
    private LocalDateTime parseDate(String dateString) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate date = LocalDate.parse(dateString, formatter);
            System.out.println(date.atStartOfDay());
            return date.atStartOfDay(); // LocalDateTime 변환
        } catch (Exception e) {
            System.out.println("Invalid date format: " + dateString);
            throw new IllegalArgumentException("Invalid date format: " + dateString, e);
        }
    }

    // Response 데이터를 카카오에 조회, Schedule 테이블에 저장.
    // activity: openai 응답받은 키워드, place: Kakaoapi 키워드 검색 응답
    private void processActivity(OpenaiResponseDto.Activity activity, Day day) {
        try {
            // 관광지 키워드 생성
            String promptResponse = activity.getName();
            // 인덱스 찾기
            int idx = promptResponse.indexOf("@");
            // 지역
            String keyword1 = promptResponse.substring(0,idx);
            // 장소명
            String keyword2 = promptResponse.substring(idx+1);
            // 키워드 검색 조회
            String keyword = keyword1 + " " + keyword2;

            KakaoRequestDto kakaoRequestDto = new KakaoRequestDto(keyword);
            KakaoResponseDto kakaoResponse = kakaoService.searchByKeyword(kakaoRequestDto);
            KakaoDto place = null;

            // 키워드 검색이 안되는 지역의 경우, 지역을 기반으로 키워드 검색.
            if (kakaoResponse == null || kakaoResponse.getPlaces().isEmpty()) {
                KakaoRequestDto newKakaoRequestDto = new KakaoRequestDto(keyword1);
                KakaoResponseDto newKakaoResponse = kakaoService.searchByKeyword(newKakaoRequestDto);
                if (newKakaoResponse != null && !newKakaoResponse.getPlaces().isEmpty()) {
                    place = newKakaoResponse.getPlaces().get(0);  // 지역으로 조회해도 안나오면 넣지 않는다.
                }
            } else {
                place = kakaoResponse.getPlaces().get(0);
            }

            Double latitude = place.getLatitude();
            Double longitude = place.getLongitude();
            // 식사 일정일 경우 좌표를 음수 -1.0으로 처리
            if (keyword2.equals("식사")) {
                latitude = -1.0;
                longitude = -1.0;
            }

            Schedule planningData = Schedule.builder()
                    .day(day)
                    .trip(day.getTrip())
                    .placeName(keyword2)
                    .orderNum(1)
                    .lat(latitude)
                    .lng(longitude)
                    .type(activity.getType())
                    .positionPath(activity.getPositionPath())
                    .duration(activity.getDuration())
                    .build();

            System.out.println("Saving planning data: " + planningData);
            scheduleRepository.save(planningData);
        } catch (Exception e) {
            System.out.println("Error processing activity " + activity.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 현재 사용자
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof UserDetails) {
            String email = ((UserDetails) principal).getUsername();  // UserDetails에서 이메일 가져오기
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }

        throw new RuntimeException("User is not authenticated");
    }
}
