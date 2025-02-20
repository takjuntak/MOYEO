package com.travel.together.TravelTogether.tripwebsocket.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RouteResponse {
    private Integer tripId;
    private List<Routes> routes;


    @Getter
    @Setter
    @NoArgsConstructor
    public static class Routes {
        private Integer dayId;
        private TransportInfo publicTransport;    // 대중교통 정보
        private TransportInfo personalVehicle;    // 개인차량 정보


        @Getter
        @Setter
        @NoArgsConstructor
        public static class TransportInfo {
            private Integer type;
            private Integer duration;  // 소요시간

        }
    }

}
