package com.llf.assistant.planner;

import lombok.Data;

import java.util.List;

@Data
public class AiAssistantPlanFields {
    private Long reservationId;
    private Long roomId;
    private String roomName;
    private String title;
    private String meetingDate;
    private String dateFrom;
    private String dateTo;
    private String startClock;
    private String endClock;
    private Integer attendees;
    private Integer rating;
    private String content;
    private String deviceRequirements;
    private String targetScope;
    private String timeRangeLabel;
    private String relativeTarget;
    private String mutationHint;
    private Integer timeShiftMinutes;
    private String quantityHint;
    private List<Long> participantUserIds;
    private Boolean allowPreempt;
    private String emergencyReason;
}
