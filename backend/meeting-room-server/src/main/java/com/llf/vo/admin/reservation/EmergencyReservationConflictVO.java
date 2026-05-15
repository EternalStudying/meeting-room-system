package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class EmergencyReservationConflictVO {
    private Long reservationId;
    private String reservationNo;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String roomLocation;
    private Long organizerId;
    private String organizerName;
    private String title;
    private Integer attendees;
    private String startTime;
    private String endTime;
    private String status;
}
