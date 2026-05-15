package com.llf.service;

import com.llf.dto.admin.reservation.EmergencyReservationRequestDTO;
import com.llf.vo.admin.reservation.EmergencyReservationConfirmVO;
import com.llf.vo.admin.reservation.EmergencyReservationPreviewVO;

public interface EmergencyReservationService {
    EmergencyReservationPreviewVO preview(EmergencyReservationRequestDTO dto, Long adminUserId);

    EmergencyReservationConfirmVO confirm(EmergencyReservationRequestDTO dto, Long adminUserId);
}
