package com.travel.together.TravelTogether.auth.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class UserResponseDto {
    private Integer id;
    private String email;
    private String name;
    private String profile;
    private String profile_image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
//    private List<String> createdTrips; // Trip 이름 또는 ID 리스트
//    private List<String> tripMemberships; // 가입된 Trip 이름 또는 ID 리스트
//    private List<String> photos; // 사진 이름 또는 경로 리스트
}