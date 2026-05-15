package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.config.GlobalExceptionHandler;
import com.llf.service.EmergencyReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminEmergencyReservationControllerTest {

    private final EmergencyReservationService emergencyReservationService = mock(EmergencyReservationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthContext.set(currentUser("USER"));
        AdminEmergencyReservationController controller = new AdminEmergencyReservationController();
        inject(controller, "emergencyReservationService", emergencyReservationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void preview_shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/emergency-reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(emergencyReservationService);
    }

    @Test
    void confirm_shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/emergency-reservations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(emergencyReservationService);
    }

    private String validBody() {
        return """
                {
                  "roomId": 101,
                  "title": "核心客户事故复盘",
                  "meetingDate": "2026-05-15",
                  "startClock": "15:00",
                  "endClock": "16:00",
                  "attendees": 6,
                  "allowPreempt": true,
                  "emergencyReason": "客户生产事故"
                }
                """;
    }

    private AuthUser currentUser(String role) {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("tester");
        user.setDisplayName("测试用户");
        user.setRole(role);
        return user;
    }

    private void inject(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
