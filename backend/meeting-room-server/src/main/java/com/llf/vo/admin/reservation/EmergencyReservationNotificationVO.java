package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class EmergencyReservationNotificationVO {
    private Long userId;
    private String displayName;
    private Long reservationId;
    private String title;
    private String reason;
}
