package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class EmergencyReservationActionVO {
    private Long reservationId;
    private String reservationTitle;
    private String actionType;
    private Long sourceRoomId;
    private String sourceRoomName;
    private Long targetRoomId;
    private String targetRoomName;
    private String reason;
}
