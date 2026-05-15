package com.llf.assistant.planner;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantToolRegistry;
import com.llf.auth.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantPlannerServiceTest {

    @Test
    void validate_validReservationListPlan_shouldBeExecutable() {
        AiAssistantPlan plan = new AiAssistantPlan();
        plan.setIntentType("operation");
        plan.setToolName("reservations.list");
        plan.setConfidence(0.92D);
        plan.getFields().setDateFrom("2026-05-15 00:00:00");
        plan.getFields().setDateTo("2026-05-16 00:00:00");
        plan.getFields().setTargetScope("mine");

        AiAssistantPlanValidator.ValidationResult result = validator().validate(plan);

        assertTrue(result.valid());
        assertTrue(result.executable());
    }

    @Test
    void validate_unknownToolName_shouldFail() {
        AiAssistantPlan plan = new AiAssistantPlan();
        plan.setIntentType("operation");
        plan.setToolName("rooms.delete");
        plan.setConfidence(0.9D);

        AiAssistantPlanValidator.ValidationResult result = validator().validate(plan);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("toolName"));
    }

    @Test
    void validate_confidenceOutsideRange_shouldFail() {
        AiAssistantPlan plan = new AiAssistantPlan();
        plan.setIntentType("operation");
        plan.setToolName("reservations.list");
        plan.setConfidence(1.2D);

        AiAssistantPlanValidator.ValidationResult result = validator().validate(plan);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("confidence"));
    }

    @Test
    void validate_writeOperationWithAmbiguity_shouldFail() {
        AiAssistantPlan plan = new AiAssistantPlan();
        plan.setIntentType("operation");
        plan.setToolName("reservations.cancel");
        plan.setConfidence(0.91D);
        plan.setAmbiguity("用户说取消这个会议室，无法确定是预约还是会议室选择");

        AiAssistantPlanValidator.ValidationResult result = validator().validate(plan);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("ambiguity"));
    }

    @Test
    void validate_knowledgeRequestWithoutToolName_shouldBeValidButNotExecutable() {
        AiAssistantPlan plan = new AiAssistantPlan();
        plan.setIntentType("knowledge");
        plan.setConfidence(0.86D);

        AiAssistantPlanValidator.ValidationResult result = validator().validate(plan);

        assertTrue(result.valid());
        assertFalse(result.executable());
    }

    @Test
    void validateJson_unknownField_shouldFail() {
        String json = """
                {
                  "intentType": "operation",
                  "toolName": "reservations.list",
                  "confidence": 0.9,
                  "fields": {
                    "dateFrom": "2026-05-15 00:00:00",
                    "dateTo": "2026-05-16 00:00:00",
                    "unexpectedField": "must fail"
                  },
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "test"
                }
                """;

        AiAssistantPlanValidator.ValidationResult result = validator().validateJson(json);

        assertFalse(result.valid());
        assertNotNull(result.reason());
    }

    private AiAssistantPlanValidator validator() {
        return new AiAssistantPlanValidator(new AiAssistantToolRegistry(List.of(new TestHandler())));
    }

    private static class TestHandler implements AiAssistantActionHandler {
        @Override
        public List<String> supportedActionTypes() {
            return List.of(
                    "overview.summary.query",
                    "overview.todaySchedule.query",
                    "calendar.query",
                    "rooms.search",
                    "rooms.detail",
                    "reservations.list",
                    "reservations.detail",
                    "reservations.create",
                    "reservations.update",
                    "reservations.cancel",
                    "reservations.review",
                    "admin.reservations.pending",
                    "admin.reservations.approve",
                    "admin.reservations.reject"
            );
        }

        @Override
        public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
            return AiAssistantActionPlan.reply("ok", List.of());
        }

        @Override
        public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
            return AiAssistantExecutionResult.error("noop", "noop", List.of());
        }
    }
}
