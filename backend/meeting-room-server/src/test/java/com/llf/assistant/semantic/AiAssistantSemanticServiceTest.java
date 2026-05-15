package com.llf.assistant.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantToolRegistry;
import com.llf.assistant.planner.AiAssistantPlanValidator;
import com.llf.assistant.planner.AiAssistantPlannerService;
import com.llf.auth.AuthUser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantSemanticServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseTemplateCases_shouldMatchExpectedIntent() throws Exception {
        AiAssistantSemanticService semanticService = createSemanticService();
        List<ColloquialCase> cases = loadCases();

        for (ColloquialCase item : cases) {
            AiAssistantIntentParseResult result = semanticService.parse(item.userText(), null);

            assertNotNull(result, item.id());
            assertEquals(item.expectedActionType(), result.getActionType(), item.id());
            assertEquals(item.expectedNeedClarification(), result.isNeedClarification(), item.id());
            assertNotNull(result.getNormalizedText(), item.id());
            assertTrue(result.getConfidence() >= 0, item.id());
            assertExpectedFields(item, result);
        }
    }

    @Test
    void parseCoreCases_shouldFallbackWhenLlmUnavailable() {
        AiAssistantSemanticService semanticService = createSemanticService();

        AiAssistantIntentParseResult tomorrowMeeting = semanticService.parse("我明天有会吗", null);
        assertEquals("reservations.list", tomorrowMeeting.getActionType());
        assertEquals("tomorrow", tomorrowMeeting.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult tomorrowReservation = semanticService.parse("我明天有预约吗", null);
        assertEquals("reservations.list", tomorrowReservation.getActionType());
        assertEquals("tomorrow", tomorrowReservation.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult todayMeetingList = semanticService.parse("我今天的会议有哪些", null);
        assertEquals("reservations.list", todayMeetingList.getActionType());
        assertEquals("today", todayMeetingList.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult tomorrowMeetingList = semanticService.parse("我明天的会议有哪些", null);
        assertEquals("reservations.list", tomorrowMeetingList.getActionType());
        assertEquals("tomorrow", tomorrowMeetingList.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult thisWeekReservations = semanticService.parse("查看我本周的预约", null);
        assertEquals("reservations.list", thisWeekReservations.getActionType());
        assertEquals("this_week", thisWeekReservations.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult tomorrowAnyReservation = semanticService.parse("我明天有没有预约", null);
        assertEquals("reservations.list", tomorrowAnyReservation.getActionType());
        assertEquals("tomorrow", tomorrowAnyReservation.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult cancelMeeting = semanticService.parse("帮我把明天下午那个会撤了", null);
        assertEquals("reservations.cancel", cancelMeeting.getActionType());
        assertTrue(cancelMeeting.isNeedClarification());

        AiAssistantIntentParseResult createMeeting = semanticService.parse("给我订个10个人的会议室", null);
        assertEquals("reservations.create", createMeeting.getActionType());
        assertEquals(10, createMeeting.getFields().getAttendees());

        AiAssistantIntentParseResult weekendMeeting = semanticService.parse("这周末我有会吗", null);
        assertEquals("reservations.list", weekendMeeting.getActionType());
        assertEquals("this_weekend", weekendMeeting.getFields().toMap().get("timeRangeLabel"));

        AiAssistantIntentParseResult moveMeeting = semanticService.parse("把我明天下午那个会往后挪半小时", null);
        assertEquals("reservations.update", moveMeeting.getActionType());
        assertEquals("delay_later", moveMeeting.getFields().toMap().get("mutationHint"));
        assertEquals(30, moveMeeting.getFields().toMap().get("timeShiftMinutes"));

        AiAssistantIntentParseResult deviceMeeting = semanticService.parse("找个带白板和投影的会议室", null);
        assertEquals("rooms.search", deviceMeeting.getActionType());
        assertEquals("投影、白板", deviceMeeting.getFields().toMap().get("deviceRequirements"));

        AiAssistantIntentParseResult pendingApproval = semanticService.parse("查看待审核预约", null);
        assertEquals("admin.reservations.pending", pendingApproval.getActionType());

        AiAssistantIntentParseResult approveReservation = semanticService.parse("通过预约 9001", null);
        assertEquals("admin.reservations.approve", approveReservation.getActionType());
        assertEquals(9001L, approveReservation.getFields().getReservationId());

        AiAssistantIntentParseResult rejectReservation = semanticService.parse("驳回预约 9001", null);
        assertEquals("admin.reservations.reject", rejectReservation.getActionType());
        assertEquals(9001L, rejectReservation.getFields().getReservationId());
    }

    @Test
    void parseInvalidLlmJson_shouldFallbackToRuleResult() {
        AiAssistantSemanticService semanticService = createSemanticService(request -> "{\"foo\":\"bar\"}");

        AiAssistantIntentParseResult result = semanticService.parse("我明天有会吗", null);

        assertEquals("reservations.list", result.getActionType());
        assertEquals("tomorrow", result.getFields().getTimeRangeLabel());
    }

    @Test
    void parse_validLlmPlannerResult_shouldWinOverRules() {
        AiAssistantSemanticService semanticService = createSemanticService(request -> """
                {
                  "intentType": "operation",
                  "toolName": "reservations.list",
                  "confidence": 0.93,
                  "fields": {
                    "dateFrom": "2026-05-15 00:00:00",
                    "dateTo": "2026-05-16 00:00:00",
                    "targetScope": "mine"
                  },
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "specified date meeting query"
                }
                """);

        AiAssistantIntentParseResult result = semanticService.parse("今天有哪些会", null);

        assertEquals("reservations.list", result.getActionType());
        assertEquals("2026-05-15 00:00:00", result.getFields().getDateFrom());
        assertEquals("2026-05-16 00:00:00", result.getFields().getDateTo());
        assertEquals("mine", result.getFields().getTargetScope());
    }

    @Test
    void parse_invalidLlmPlannerResults_shouldFallbackToRules() {
        for (String llmJson : List.of(
                "",
                "not json",
                """
                        {
                          "intentType": "operation",
                          "toolName": "rooms.delete",
                          "confidence": 0.95,
                          "fields": {},
                          "missingFields": [],
                          "ambiguity": null,
                          "reason": "unknown tool"
                        }
                        """,
                """
                        {
                          "intentType": "operation",
                          "toolName": "reservations.list",
                          "confidence": 0.2,
                          "fields": {},
                          "missingFields": [],
                          "ambiguity": null,
                          "reason": "low confidence"
                        }
                        """)) {
            AiAssistantSemanticService semanticService = createSemanticService(request -> llmJson);

            AiAssistantIntentParseResult result = semanticService.parse("明天有哪些会", null);

            assertEquals("reservations.list", result.getActionType(), llmJson);
            assertEquals("tomorrow", result.getFields().getTimeRangeLabel(), llmJson);
        }
    }

    @Test
    void parse_llmNaturalLanguageDateFields_shouldNormalizeBeforeExecution() {
        AiAssistantSemanticService semanticService = createSemanticService(request -> """
                {
                  "intentType": "operation",
                  "toolName": "calendar.query",
                  "confidence": 0.92,
                  "fields": {
                    "dateFrom": "下周开始日期",
                    "dateTo": "下周结束日期",
                    "timeRangeLabel": "next_week"
                  },
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "next week schedule"
                }
                """);

        AiAssistantIntentParseResult result = semanticService.parse("下周我有哪些日程", null);
        LocalDate nextMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));

        assertEquals("calendar.query", result.getActionType());
        assertEquals("next_week", result.getFields().getTimeRangeLabel());
        assertEquals(nextMonday + " 00:00:00", result.getFields().getDateFrom());
        assertEquals(nextMonday.plusDays(7) + " 00:00:00", result.getFields().getDateTo());
    }

    @Test
    void parse_llmWrongToolForRoomAvailability_shouldFallbackToRules() {
        AiAssistantSemanticService semanticService = createSemanticService(request -> """
                {
                  "intentType": "operation",
                  "toolName": "calendar.query",
                  "confidence": 0.91,
                  "fields": {
                    "dateFrom": "明天",
                    "dateTo": "明天"
                  },
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "wrong tool"
                }
                """);

        AiAssistantIntentParseResult result = semanticService.parse("明天9点到11点有哪些会议室可以用", null);

        assertEquals("rooms.search", result.getActionType());
        assertEquals(LocalDate.now().plusDays(1).toString(), result.getFields().getMeetingDate());
        assertEquals("09:00", result.getFields().getStartClock());
        assertEquals("11:00", result.getFields().getEndClock());
    }

    @Test
    void parse_llmWrongToolForMeetingList_shouldFallbackToReservationsList() {
        AiAssistantSemanticService semanticService = createSemanticService(request -> """
                {
                  "intentType": "operation",
                  "toolName": "calendar.query",
                  "confidence": 0.91,
                  "fields": {
                    "dateFrom": "2026-05-15 00:00:00",
                    "dateTo": "2026-05-16 00:00:00"
                  },
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "wrong tool"
                }
                """);

        AiAssistantIntentParseResult result = semanticService.parse("2026-05-15有哪些会议", null);

        assertEquals("reservations.list", result.getActionType());
        assertEquals("2026-05-15 00:00:00", result.getFields().getDateFrom());
        assertEquals("2026-05-16 00:00:00", result.getFields().getDateTo());
    }

    @Test
    void parseFailureSamples_shouldResolveExpectedActionsAndTimeFields() {
        AiAssistantSemanticService semanticService = createSemanticService();

        AiAssistantIntentParseResult specificDate = semanticService.parse("2026-05-15有哪些会议", null);
        assertEquals("reservations.list", specificDate.getActionType());
        assertEquals("2026-05-15 00:00:00", specificDate.getFields().getDateFrom());
        assertEquals("2026-05-16 00:00:00", specificDate.getFields().getDateTo());

        AiAssistantIntentParseResult lastWeek = semanticService.parse("上周我参加了哪些会议", null);
        LocalDate thisMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        assertEquals("reservations.list", lastWeek.getActionType());
        assertEquals("last_week", lastWeek.getFields().getTimeRangeLabel());
        assertEquals(thisMonday.minusDays(7) + " 00:00:00", lastWeek.getFields().getDateFrom());
        assertEquals(thisMonday + " 00:00:00", lastWeek.getFields().getDateTo());

        AiAssistantIntentParseResult nextWeekSchedule = semanticService.parse("下周我有哪些日程", null);
        LocalDate nextMonday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        assertEquals("calendar.query", nextWeekSchedule.getActionType());
        assertEquals("next_week", nextWeekSchedule.getFields().getTimeRangeLabel());
        assertEquals(nextMonday + " 00:00:00", nextWeekSchedule.getFields().getDateFrom());
        assertEquals(nextMonday.plusDays(7) + " 00:00:00", nextWeekSchedule.getFields().getDateTo());

        AiAssistantIntentParseResult roomAvailability = semanticService.parse("明天9点到11点有哪些会议室可以用", null);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertEquals("rooms.search", roomAvailability.getActionType());
        assertEquals(tomorrow.toString(), roomAvailability.getFields().getMeetingDate());
        assertEquals("09:00", roomAvailability.getFields().getStartClock());
        assertEquals("11:00", roomAvailability.getFields().getEndClock());

        AiAssistantIntentParseResult cancelThisRoom = semanticService.parse("取消这个会议室", null);
        assertEquals("unknown", cancelThisRoom.getActionType());
        assertTrue(cancelThisRoom.isNeedClarification());
        assertTrue(cancelThisRoom.getClarificationReason().contains("取消预约") || cancelThisRoom.getClarificationReason().contains("放弃当前选择"));
    }

    private AiAssistantSemanticService createSemanticService() {
        return createSemanticService(request -> null);
    }

    private AiAssistantSemanticService createSemanticService(AiAssistantIntentLlmClient llmClient) {
        AiAssistantToolRegistry toolRegistry = new AiAssistantToolRegistry(List.of(new TestHandler()));
        AiAssistantIntentSchemaValidator schemaValidator = new AiAssistantIntentSchemaValidator();
        AiAssistantPlanValidator planValidator = new AiAssistantPlanValidator(toolRegistry);
        return new AiAssistantSemanticService(
                new AiAssistantTextNormalizer(),
                new AiAssistantTimeResolver(),
                new AiAssistantIntentParser(toolRegistry, schemaValidator, request -> null),
                schemaValidator,
                new AiAssistantReferenceResolver(),
                new AiAssistantPlannerService(llmClient, planValidator, toolRegistry)
        );
    }

    private List<ColloquialCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ai/assistant-colloquial-cases.template.json")) {
            assertNotNull(inputStream);
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private void assertExpectedFields(ColloquialCase item, AiAssistantIntentParseResult result) {
        Map<String, Object> expectedFields = item.expectedFields();
        if (expectedFields == null || expectedFields.isEmpty()) {
            return;
        }
        Map<String, Object> actualFields = result.getFields().toMap();
        for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
            assertEquals(resolveExpectedFieldValue(item, entry), actualFields.get(entry.getKey()), item.id() + ":" + entry.getKey());
        }
    }

    private Object resolveExpectedFieldValue(ColloquialCase item, Map.Entry<String, Object> entry) {
        if ("meetingDate".equals(entry.getKey()) && "tomorrow_afternoon".equals(item.expectedFields().get("timeRangeLabel"))) {
            return LocalDate.now().plusDays(1).toString();
        }
        return entry.getValue();
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

    private record ColloquialCase(String id,
                                  String userText,
                                  String expectedActionType,
                                  boolean expectedNeedClarification,
                                  Map<String, Object> expectedFields,
                                  String notes) {
    }
}
