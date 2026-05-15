package com.llf.dto.admin.reservation;

import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EmergencyReservationRequestDTO {

    @NotNull(message = "roomId must not be null")
    private Long roomId;

    @NotBlank(message = "title must not be blank")
    private String title;

    @NotBlank(message = "meetingDate must not be blank")
    private String meetingDate;

    @NotBlank(message = "startClock must not be blank")
    private String startClock;

    @NotBlank(message = "endClock must not be blank")
    private String endClock;

    @NotNull(message = "attendees must not be null")
    @Min(value = 1, message = "attendees must be greater than 0")
    private Integer attendees;

    private Boolean allowPreempt;

    @NotBlank(message = "emergencyReason must not be blank")
    private String emergencyReason;

    private String remark;

    @Valid
    private List<ReservationDeviceRequirementDTO> deviceRequirements;

    private List<Long> participantUserIds;
}
