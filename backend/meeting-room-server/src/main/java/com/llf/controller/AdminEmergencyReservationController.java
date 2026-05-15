package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.dto.admin.reservation.EmergencyReservationRequestDTO;
import com.llf.result.BizException;
import com.llf.result.R;
import com.llf.service.EmergencyReservationService;
import com.llf.vo.admin.reservation.EmergencyReservationConfirmVO;
import com.llf.vo.admin.reservation.EmergencyReservationPreviewVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/emergency-reservations")
public class AdminEmergencyReservationController {

    @Resource
    private EmergencyReservationService emergencyReservationService;

    @PostMapping("/preview")
    public R<EmergencyReservationPreviewVO> preview(@Valid @RequestBody EmergencyReservationRequestDTO dto) {
        AuthUser admin = requireAdmin();
        return R.ok(emergencyReservationService.preview(dto, admin.getId()));
    }

    @PostMapping("/confirm")
    public R<EmergencyReservationConfirmVO> confirm(@Valid @RequestBody EmergencyReservationRequestDTO dto) {
        AuthUser admin = requireAdmin();
        return R.ok(emergencyReservationService.confirm(dto, admin.getId()));
    }

    private AuthUser requireAdmin() {
        AuthUser user = AuthContext.get();
        if (user == null || !isAdminRole(user.getRole())) {
            throw new BizException(403, "admin permission required");
        }
        return user;
    }

    private boolean isAdminRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String value = role.trim();
        return "ADMIN".equalsIgnoreCase(value) || "2".equals(value) || "admin".equalsIgnoreCase(value);
    }
}
