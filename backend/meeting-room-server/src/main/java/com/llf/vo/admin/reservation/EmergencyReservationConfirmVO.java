package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class EmergencyReservationConfirmVO {
    private Long reservationId;
    private String reservationNo;
    private String status;
    private String message;
    private EmergencyReservationPreviewVO executedPlan;
}
