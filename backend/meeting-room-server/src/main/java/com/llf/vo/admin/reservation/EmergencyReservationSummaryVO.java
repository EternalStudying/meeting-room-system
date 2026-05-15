package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class EmergencyReservationSummaryVO {
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String title;
    private Integer attendees;
    private String startTime;
    private String endTime;
    private String emergencyReason;
}
