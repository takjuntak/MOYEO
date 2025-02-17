package com.travel.together.TravelTogether.tripwebsocket.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class EditRequest {
    private String operationId;
    private Integer tripId;
    private Operation operation;
    private String timestamp;

    public EditRequest(Integer tripId, Operation operation) {
    }



    @Getter
    @Setter
    @NoArgsConstructor
    public static class Operation {
        private String action;  // MOVE, DELETE, START
        private Integer scheduleId;  // START일떄 0
//        private Integer fromPosition;
//        private Integer toPosition;
        private Integer positionPath; // START 일떄 0
    }

}



