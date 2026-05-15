package com.llf.vo.admin.reservation;

import lombok.Data;

import java.util.List;

@Data
public class EmergencyReservationPreviewVO {
    private EmergencyReservationSummaryVO emergencySummary;
    private List<EmergencyReservationConflictVO> conflicts;
    private List<EmergencyReservationActionVO> actions;
    private List<EmergencyReservationNotificationVO> notifications;
    private Boolean canExecute;
    private String message;
}
