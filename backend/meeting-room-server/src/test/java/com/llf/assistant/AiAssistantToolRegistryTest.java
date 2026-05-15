package com.llf.assistant;

import com.llf.auth.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantToolRegistryTest {

    @Test
    void registry_shouldExposeAllPhaseOneToolsWithMetadata() {
        AiAssistantToolRegistry registry = new AiAssistantToolRegistry(List.of(
                handler("overview.summary.query", "overview.todaySchedule.query"),
                handler("calendar.query"),
                handler("rooms.search", "rooms.detail"),
                handler("reservations.list", "reservations.detail", "reservations.create", "reservations.update", "reservations.cancel", "reservations.review"),
                handler("admin.reservations.pending", "admin.reservations.approve", "admin.reservations.reject")
        ));

        assertEquals(Set.of(
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
        ), registry.toolNames());

        AiAssistantToolDefinition create = registry.require("reservations.create");
        assertEquals("write", create.getOperationType());
        assertEquals("USER", create.getRequiredPermission());
        assertTrue(create.isConfirmRequired());
        assertEquals("confirmation", create.getResultCardType());
        assertFalse(create.getRequiredFields().isEmpty());

        AiAssistantToolDefinition pending = registry.require("admin.reservations.pending");
        assertEquals("read", pending.getOperationType());
        assertEquals("ADMIN", pending.getRequiredPermission());
        assertFalse(pending.isConfirmRequired());
        assertEquals("query_result", pending.getResultCardType());
    }

    @Test
    void registry_shouldDetectStableColloquialReservationQueries() {
        AiAssistantToolRegistry registry = new AiAssistantToolRegistry(List.of(handler("reservations.list")));

        assertEquals("reservations.list", registry.detectToolName("明天有哪些会"));
        assertEquals("reservations.list", registry.detectToolName("明天有什么安排"));
        assertEquals("reservations.list", registry.detectToolName("明天有哪些预约"));
        assertEquals("reservations.list", registry.detectToolName("我明天有会吗"));
    }

    private AiAssistantActionHandler handler(String... toolNames) {
        return new AiAssistantActionHandler() {
            @Override
            public List<String> supportedActionTypes() {
                return List.of(toolNames);
            }

            @Override
            public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
                return AiAssistantActionPlan.reply("ok", List.of());
            }

            @Override
            public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
                return AiAssistantExecutionResult.success("ok", "ok", List.of(), null);
            }
        };
    }
}
