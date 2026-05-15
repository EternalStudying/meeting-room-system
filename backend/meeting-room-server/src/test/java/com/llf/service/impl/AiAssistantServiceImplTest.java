package com.llf.service.impl;

import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantToolRegistry;
import com.llf.assistant.handler.CalendarAssistantActionHandler;
import com.llf.assistant.handler.AdminReservationAssistantActionHandler;
import com.llf.assistant.handler.AdminEmergencyReservationAssistantActionHandler;
import com.llf.assistant.handler.OverviewAssistantActionHandler;
import com.llf.assistant.handler.ReservationAssistantActionHandler;
import com.llf.assistant.handler.RoomAssistantActionHandler;
import com.llf.assistant.planner.AiAssistantPlanValidator;
import com.llf.assistant.planner.AiAssistantPlannerService;
import com.llf.assistant.planner.AiAssistantRequestRouter;
import com.llf.assistant.rag.AiAssistantKnowledgeService;
import com.llf.assistant.rag.AiAssistantRagService;
import com.llf.assistant.semantic.AiAssistantIntentParser;
import com.llf.assistant.semantic.AiAssistantReferenceResolver;
import com.llf.assistant.semantic.AiAssistantIntentSchemaValidator;
import com.llf.assistant.semantic.AiAssistantIntentLlmClient;
import com.llf.assistant.semantic.AiAssistantSemanticService;
import com.llf.assistant.semantic.AiAssistantTextNormalizer;
import com.llf.assistant.semantic.AiAssistantTimeResolver;
import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.result.BizException;
import com.llf.service.DashboardService;
import com.llf.service.EmergencyReservationService;
import com.llf.service.ReservationService;
import com.llf.service.RoomService;
import com.llf.service.UserService;
import com.llf.vo.assistant.AiAssistantTurnVO;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.admin.reservation.AdminReservationPageVO;
import com.llf.vo.dashboard.DashboardOverviewVO;
import com.llf.vo.dashboard.DashboardOverviewSummaryVO;
import com.llf.vo.reservation.CalendarEventVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.room.RoomPageDataVO;
import com.llf.vo.room.RoomPageItemVO;
import com.llf.vo.room.RoomDeviceOptionVO;
import com.llf.vo.user.UserOptionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private RoomService roomService;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private UserService userService;

    @Mock
    private EmergencyReservationService emergencyReservationService;

    private AiAssistantServiceImpl aiAssistantService;

    @BeforeEach
    void setUp() {
        aiAssistantService = createService(request -> null);
        AuthContext.set(currentUser(1L));
    }

    private AiAssistantServiceImpl createService(AiAssistantIntentLlmClient llmClient) {
        AiAssistantToolRegistry registry = new AiAssistantToolRegistry(List.of(
                new AdminEmergencyReservationAssistantActionHandler(emergencyReservationService, roomService, userService),
                new AdminReservationAssistantActionHandler(reservationService),
                new ReservationAssistantActionHandler(reservationService, userService, roomService),
                new RoomAssistantActionHandler(reservationService, roomService),
                new OverviewAssistantActionHandler(dashboardService),
                new CalendarAssistantActionHandler(reservationService)
        ));
        AiAssistantIntentSchemaValidator schemaValidator = new AiAssistantIntentSchemaValidator();
        AiAssistantPlanValidator planValidator = new AiAssistantPlanValidator(registry);
        AiAssistantSemanticService semanticService = new AiAssistantSemanticService(
                new AiAssistantTextNormalizer(),
                new AiAssistantTimeResolver(),
                new AiAssistantIntentParser(registry, schemaValidator, request -> null),
                schemaValidator,
                new AiAssistantReferenceResolver(),
                new AiAssistantPlannerService(llmClient, planValidator, registry)
        );
        return new AiAssistantServiceImpl(
                new AiAssistantSessionStore(),
                registry,
                semanticService,
                new AiAssistantRequestRouter(),
                new AiAssistantRagService(new AiAssistantKnowledgeService())
        );
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createSession_shouldReturnReplyStage() {
        AiAssistantTurnVO result = aiAssistantService.createSession(currentUser(1L));

        assertNotNull(result.getSessionId());
        assertTrue(result.getSessionId().startsWith("asst-"));
        assertNotNull(result.getTurnId());
        assertEquals("assistant", result.getRole());
        assertEquals("idle", result.getState());
        assertTrue(result.getCards().stream().anyMatch(card -> "text".equals(card.getType())));
        assertTrue(result.getSuggestions().size() >= 3);
    }

    @Test
    void message_tomorrowMeetingsWhenLlmUnavailable_shouldReturnQueryResultCard() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "明天有哪些会"));

        assertEquals("executed", result.getState());
        assertTrue(result.getMessage().contains("明天"));
        assertTrue(result.getCards().stream().anyMatch(card ->
                "query_result".equals(card.getType()) && card.getMessage().contains("项目周会")));
    }

    @Test
    void message_vagueTemporalRequestWithLlmCalendarPlan_shouldStillClarify() {
        aiAssistantService = createService(request -> """
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
                  "reason": "vague tomorrow request"
                }
                """);
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我看看明天的"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("查询预约") || result.getAssistantText().contains("查看日历"));
        assertTrue(result.getCards().stream().anyMatch(card -> "clarification".equals(card.getType())));
    }

    @Test
    void message_todayOverview_shouldQueryOverviewNotRagHelp() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(dashboardService.getOverview()).thenReturn(overview(2, 3, 4, 65));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "今天概览怎么样"));

        assertEquals("reply", result.getStage());
        assertEquals("executed", result.getState());
        assertTrue(result.getAssistantText().contains("今日共有 2 场会议"));
        assertTrue(result.getAssistantText().contains("待处理预约 3 条"));
        assertTrue(result.getCards().stream().anyMatch(card ->
                "query_result".equals(card.getType()) && "查询系统概览".equals(card.getTitle())));
        verify(dashboardService).getOverview();
    }

    @Test
    void message_todayOverviewWithWrongLlmSchedulePlan_shouldStillQueryOverview() {
        aiAssistantService = createService(request -> """
                {
                  "intentType": "operation",
                  "toolName": "overview.todaySchedule.query",
                  "confidence": 0.93,
                  "fields": {},
                  "missingFields": [],
                  "ambiguity": null,
                  "reason": "today overview question"
                }
                """);
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(dashboardService.getOverview()).thenReturn(overview(2, 3, 4, 65));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "今天概览怎么样"));

        assertEquals("reply", result.getStage());
        assertEquals("executed", result.getState());
        assertTrue(result.getAssistantText().contains("今日共有 2 场会议"));
        assertTrue(result.getCards().stream().anyMatch(card ->
                "query_result".equals(card.getType()) && "查询系统概览".equals(card.getTitle())));
        verify(dashboardService).getOverview();
    }

    @Test
    void message_specificDateMeetings_shouldQueryMyReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), eq("2026-05-15 00:00:00"), eq("2026-05-16 00:00:00"), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9101L, "需求评审会", "2026-05-15 09:00:00", "2026-05-15 10:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "2026-05-15有哪些会议"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("需求评审会"));
        verify(reservationService).myReservations(eq(1L), eq("2026-05-15 00:00:00"), eq("2026-05-16 00:00:00"), eq("all"), eq(null), eq(false));
    }

    @Test
    void message_lastWeekParticipatedMeetings_shouldQueryPreviousWeekReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        String start = thisMonday.minusDays(7) + " 00:00:00";
        String end = thisMonday + " 00:00:00";
        when(reservationService.myReservations(eq(1L), eq(start), eq(end), eq("participant"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9102L, "上周复盘会", thisMonday.minusDays(3) + " 10:00:00", thisMonday.minusDays(3) + " 11:00:00", "潮汐会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "上周我参加了哪些会议"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("上周复盘会"));
        verify(reservationService).myReservations(eq(1L), eq(start), eq(end), eq("participant"), eq(null), eq(false));
    }

    @Test
    void message_organizerAndParticipantQueries_shouldUseSpecificScope() {
        when(reservationService.myReservations(eq(1L), any(), any(), eq("organizer"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9201L, "我发起的周会", "2026-05-16 09:00:00", "2026-05-16 10:00:00", "云杉会议室")));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("participant"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9202L, "我参与的培训", "2026-05-17 09:00:00", "2026-05-17 10:00:00", "潮汐会议室")));

        AiAssistantTurnVO organizerSession = aiAssistantService.createSession(currentUser(1L));
        AiAssistantTurnVO organizerResult = aiAssistantService.message(currentUser(1L), messageRequest(organizerSession.getSessionId(), "我发起了哪些会议"));

        AiAssistantTurnVO participantSession = aiAssistantService.createSession(currentUser(1L));
        AiAssistantTurnVO participantResult = aiAssistantService.message(currentUser(1L), messageRequest(participantSession.getSessionId(), "我参与了哪些会议"));

        assertEquals("reply", organizerResult.getStage());
        assertTrue(organizerResult.getAssistantText().contains("我发起的周会"));
        assertFalse(organizerResult.getAssistantText().contains("我参与的培训"));
        assertEquals("reply", participantResult.getStage());
        assertTrue(participantResult.getAssistantText().contains("我参与的培训"));
        assertFalse(participantResult.getAssistantText().contains("我发起的周会"));
        verify(reservationService).myReservations(eq(1L), any(), any(), eq("organizer"), eq(null), eq(false));
        verify(reservationService).myReservations(eq(1L), any(), any(), eq("participant"), eq(null), eq(false));
    }

    @Test
    void message_nextWeekSchedule_shouldQueryNextWeekCalendar() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String start = nextMonday + " 00:00:00";
        String end = nextMonday.plusDays(7) + " 00:00:00";
        when(reservationService.listCalendar(eq(start), eq(end), eq(null), eq("ACTIVE")))
                .thenReturn(List.of(calendarEvent(9103L, "下周排期会", nextMonday.plusDays(1) + " 09:30:00", nextMonday.plusDays(1) + " 10:30:00", "梧桐会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "下周我有哪些日程"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("下周排期会"));
        verify(reservationService).listCalendar(eq(start), eq(end), eq(null), eq("ACTIVE"));
    }

    @Test
    void message_roomAvailabilityWithTimeWindow_shouldRecommendRooms() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ReservationRecommendationVO recommendation = recommendation(List.of(recommendationItem(101L, "A101 多媒体会议室", "1号楼", 12)));
        when(reservationService.recommend(any(ReservationRecommendationDTO.class))).thenReturn(recommendation);

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "明天9点到11点有哪些会议室可以用"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("A101 多媒体会议室"));
        ArgumentCaptor<ReservationRecommendationDTO> captor = ArgumentCaptor.forClass(ReservationRecommendationDTO.class);
        verify(reservationService).recommend(captor.capture());
        assertEquals(1, captor.getValue().getAttendees());
        assertEquals(tomorrow + " 09:00:00", captor.getValue().getStartTime());
        assertEquals(tomorrow + " 11:00:00", captor.getValue().getEndTime());
    }

    @Test
    void message_roomDetailByCodeOrName_shouldReturnDetail() {
        RoomPageItemVO room = room(101L, "云杉会议室", "R-A101", "A座 1层", 12);
        when(roomService.pageRooms(1, 20, null, null, null, null, null)).thenReturn(roomPage(List.of(room)));
        when(roomService.userDetailById(101L)).thenReturn(room);

        AiAssistantTurnVO codeSession = aiAssistantService.createSession(currentUser(1L));
        AiAssistantTurnVO codeResult = aiAssistantService.message(currentUser(1L), messageRequest(codeSession.getSessionId(), "A101 会议室详情"));
        assertEquals("reply", codeResult.getStage());
        assertTrue(codeResult.getAssistantText().contains("云杉会议室"));
        assertTrue(codeResult.getAssistantText().contains("A座 1层"));

        AiAssistantTurnVO fullCodeSession = aiAssistantService.createSession(currentUser(1L));
        AiAssistantTurnVO fullCodeResult = aiAssistantService.message(currentUser(1L), messageRequest(fullCodeSession.getSessionId(), "R-A101 会议室详情"));
        assertEquals("reply", fullCodeResult.getStage());
        assertTrue(fullCodeResult.getAssistantText().contains("云杉会议室"));

        AiAssistantTurnVO nameSession = aiAssistantService.createSession(currentUser(1L));
        AiAssistantTurnVO nameResult = aiAssistantService.message(currentUser(1L), messageRequest(nameSession.getSessionId(), "查看云杉会议室详情"));
        assertEquals("reply", nameResult.getStage());
        assertTrue(nameResult.getAssistantText().contains("云杉会议室"));
    }

    @Test
    void message_cancelThisRoom_shouldClarifyInsteadOfExecuting() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消这个会议室"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("取消预约") || result.getAssistantText().contains("放弃当前选择"));
        assertTrue(result.getCards().stream().anyMatch(card -> "clarification".equals(card.getType())));
        assertEquals(null, result.getPendingAction());
    }

    @Test
    void message_userAdminApprove_shouldReturnPermissionErrorCard() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "通过预约 9001"));

        assertEquals("error", result.getState());
        assertTrue(result.getCards().stream().anyMatch(card ->
                "error".equals(card.getType()) && card.getMessage().contains("管理员")));
    }

    @Test
    void message_adminApproveReservation_shouldReturnConfirmationCard() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));

        AiAssistantTurnVO result = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "通过预约 9001"));

        assertEquals("awaiting_confirmation", result.getState());
        assertTrue(result.getCards().stream().anyMatch(card ->
                "confirmation".equals(card.getType())
                        && card.getPendingAction() != null
                        && "admin.reservations.approve".equals(card.getPendingAction().getActionType())));
    }

    @Test
    void message_textCreate_shouldReturnCollect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "title".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "meetingDate".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "startClock".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "endClock".equals(field.getKey())));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "attendees".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "participantUserIds".equals(field.getKey())
                        && "user-select".equals(field.getInputType())
                        && !field.isRequired()));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "deviceRequirements".equals(field.getKey())));
    }

    @Test
    void message_afterBaseCreateFields_shouldCollectDeviceRequirementsBeforeRoomRecommendation() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "participantUserIds", List.of(201)
        ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), request);

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "deviceRequirements".equals(field.getKey())
                        && "device-requirements".equals(field.getInputType())
                        && !field.isRequired()));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "roomId".equals(field.getKey())));
    }

    @Test
    void message_collectFieldsForCreate_shouldEnterConfirm() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "deviceRequirements", List.of(),
                "roomId", 101
        ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), request);

        assertEquals("confirm", result.getStage());
        assertNotNull(result.getPendingAction());
        assertEquals("reservations.create", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getConfirmRequired());
    }

    @Test
    void confirm_afterCreate_shouldExecuteAndReturnResult() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "deviceRequirements", List.of(),
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2001L);
        created.setReservationNo("R20260418001");
        created.setRoomId(101L);
        created.setTitle("周会复盘");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("success", result.getResult().getStatus());
        assertEquals("/reservations/index", result.getResult().getDeepLink());

        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals("周会复盘", captor.getValue().getTitle());
        assertEquals(101L, captor.getValue().getRoomId());
        assertEquals(1, captor.getValue().getAttendees());
    }

    @Test
    void message_createWithAmbiguousParticipant_shouldReturnCollectUserSelect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(userService.searchActiveUsersByDisplayName("张三", 10, 1L)).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三"),
                userOption(102L, "zhangsan2", "张三")
        ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我约一个我和张三的会"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("参会人"));
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "participantUserIds".equals(field.getKey())
                        && "user-select".equals(field.getInputType())));
        verify(userService).searchActiveUsersByDisplayName("张三", 10, 1L);
    }

    @Test
    void confirm_afterCreateWithRecognizedParticipants_shouldPassParticipantIds() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(userService.searchActiveUsersByDisplayName("张三", 10, 1L)).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三")
        ));

        AiAssistantTurnVO firstTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我约一个我和张三的会"));
        assertEquals("collect", firstTurn.getStage());

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "deviceRequirements", List.of(),
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2002L);
        created.setReservationNo("R20260418002");
        created.setRoomId(101L);
        created.setTitle("周会复盘");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals(List.of(101L), captor.getValue().getParticipantUserIds());
        assertEquals(2, captor.getValue().getAttendees());
    }

    @Test
    void confirm_afterCreateWithParticipantsAndDevices_shouldPassDerivedAttendeesAndDeviceRequirements() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "roomId", 101,
                "participantUserIds", List.of(201, 202),
                "deviceRequirements", List.of(Map.of("deviceId", 1, "quantity", 2))
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2003L);
        created.setReservationNo("R20260418003");
        created.setRoomId(101L);
        created.setTitle("周会复盘");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals(3, captor.getValue().getAttendees());
        assertEquals(List.of(201L, 202L), captor.getValue().getParticipantUserIds());
        List<ReservationDeviceRequirementDTO> requirements = captor.getValue().getDeviceRequirements();
        assertNotNull(requirements);
        assertEquals(1, requirements.size());
        assertEquals(1L, requirements.get(0).getDeviceId());
        assertEquals(2, requirements.get(0).getQuantity());
    }

    @Test
    void confirm_afterUnrelatedMessage_shouldStillExecuteFrozenParams() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "冻结参数会议",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "deviceRequirements", List.of(),
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);
        String executionId = confirmTurn.getPendingAction().getExecutionId();
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false))).thenReturn(List.of());
        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2004L);
        created.setReservationNo("R20260418004");
        created.setRoomId(101L);
        created.setTitle("冻结参数会议");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        AiAssistantTurnVO queryTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我本周的预约"));
        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), executionId);

        assertEquals("reply", queryTurn.getStage());
        assertEquals("result", result.getStage());
        assertEquals("success", result.getResult().getStatus());
        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals("冻结参数会议", captor.getValue().getTitle());
        assertEquals("2026-04-20", captor.getValue().getMeetingDate());
    }

    @Test
    void cancel_shouldReturnCancelledResult() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "attendees", 10,
                "deviceRequirements", List.of(),
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        AiAssistantTurnVO result = aiAssistantService.cancel(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("cancelled", result.getResult().getStatus());
    }

    @Test
    void message_deleteReservation_shouldMapToCancel() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "删除我明天下午的预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "cancelReason".equals(field.getKey())));
    }

    @Test
    void message_cancelWhenMultipleMatches_shouldReturnReservationSelect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(
                        activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室"),
                        activeReservation(9002L, "客户沟通会", "2026-04-19 15:30:00", "2026-04-19 16:00:00", "潮汐会议室")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消我明天下午的预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "reservationId".equals(field.getKey()) && "select".equals(field.getInputType())));
        assertFalse(result.getMissingFields().stream().filter(field -> "reservationId".equals(field.getKey())).findFirst().orElseThrow().getOptions().isEmpty());
    }

    @Test
    void message_selectedUnavailableReservation_shouldReturnErrorInsteadOfAskingTargetAgain() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        List<MyReservationVO> reservations = List.of(
                activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室"),
                activeReservation(9002L, "客户沟通会", "2026-04-19 15:30:00", "2026-04-19 16:00:00", "潮汐会议室")
        );
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(reservations, reservations);
        when(reservationService.myReservationDetail(9002L, 1L))
                .thenThrow(new BizException(404, "reservation not found"));

        AiAssistantTurnVO selectTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消我明天下午的预约"));
        assertEquals("collect", selectTurn.getStage());

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "reservationId", 9002,
                "cancelReason", "需求取消，会议无需继续"
        ));
        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), request);

        assertEquals("error", result.getStage());
        assertTrue(result.getAssistantText().contains("不可用") || result.getAssistantText().contains("无权"));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "reservationId".equals(field.getKey())));
    }

    @Test
    void message_cancelSingleMatchWithoutReason_shouldCollectCancelReasonBeforeConfirm() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消我明天下午的预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("取消原因"));
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "cancelReason".equals(field.getKey()) && "textarea".equals(field.getInputType())));
        assertEquals(null, result.getPendingAction());
    }

    @Test
    void message_cancelNumericTitleWithLlmReservationId_shouldMatchTitleBeforeUnavailableError() {
        aiAssistantService = createService(request -> {
            if (!request.originalText().contains("取消")) {
                return null;
            }
            return """
                    {
                      "intentType": "operation",
                      "toolName": "reservations.cancel",
                      "confidence": 0.92,
                      "fields": {
                        "reservationId": 111,
                        "targetScope": "mine"
                      },
                      "missingFields": [],
                      "ambiguity": null,
                      "reason": "numeric meeting title was mistaken as id"
                    }
                    """;
        });
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO reservation = activeReservation(9001L, "111", "2026-05-15 09:00:00", "2026-05-15 10:00:00", "A201 董事会议室");
        when(reservationService.myReservationDetail(111L, 1L))
                .thenThrow(new BizException(404, "reservation not found"));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(reservation));
        when(reservationService.cancelMyReservation(eq(9001L), eq(1L), any()))
                .thenReturn(reservation);

        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "把111的会议取消，原因需求取消"));
        assertEquals("confirm", confirmTurn.getStage());
        assertNotNull(confirmTurn.getPendingAction());
        assertEquals("reservations.cancel", confirmTurn.getPendingAction().getActionType());

        aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        verify(reservationService).cancelMyReservation(eq(9001L), eq(1L), any());
    }

    @Test
    void message_cancelParticipantReservation_shouldReturnNotCancelableError() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO participantReservation = activeReservation(9002L, "客户沟通会", "2026-04-19 15:30:00", "2026-04-19 16:00:00", "潮汐会议室");
        participantReservation.setRole("PARTICIPANT");
        participantReservation.setCanCancel(Boolean.FALSE);
        participantReservation.setCanEdit(Boolean.FALSE);
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(participantReservation));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消我明天下午的预约"));

        assertEquals("error", result.getStage());
        assertTrue(result.getAssistantText().contains("不能取消"));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "reservationId".equals(field.getKey())));
    }

    @Test
    void message_updateParticipantReservation_shouldReturnNotEditableError() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO participantReservation = activeReservation(9002L, "客户沟通会", "2026-04-19 15:30:00", "2026-04-19 16:00:00", "潮汐会议室");
        participantReservation.setRole("PARTICIPANT");
        participantReservation.setCanCancel(Boolean.FALSE);
        participantReservation.setCanEdit(Boolean.FALSE);
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(participantReservation));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "修改我明天下午的预约"));

        assertEquals("error", result.getStage());
        assertTrue(result.getAssistantText().contains("不能修改"));
        assertFalse(result.getMissingFields().stream().anyMatch(field -> "reservationId".equals(field.getKey())));
    }

    @Test
    void confirm_withInvalidExecutionId_shouldReturnErrorStage() {
        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), "exec-invalid");

        assertEquals("error", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("error", result.getResult().getStatus());
    }

    @Test
    void message_colloquialTomorrowMeetings_shouldReturnReply() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
    }

    @Test
    void message_outOfScopeText_shouldReturnCapabilityHintInsteadOfClarification() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "讲个笑话"));

        assertEquals("reply", result.getStage());
        assertEquals("idle", result.getState());
        assertTrue(result.getAssistantText().contains("我当前只支持") || result.getAssistantText().contains("可以帮你"));
        assertTrue(result.getMissingFields().isEmpty());
    }

    @Test
    void message_cancelRuleQuestion_shouldReturnRagAnswerWithoutPendingAction() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "怎么取消预约"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("取消"));
        assertEquals(null, result.getPendingAction());
        assertTrue(result.getCards().stream().anyMatch(card -> "text".equals(card.getType()) && "系统帮助".equals(card.getTitle())));
    }

    @Test
    void message_adminRejectRuleQuestion_shouldReturnRagAnswerWithoutPendingAction() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "审批驳回需要填原因吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("驳回"));
        assertTrue(result.getAssistantText().contains("原因"));
        assertEquals(null, result.getPendingAction());
    }

    @Test
    void message_weatherQuestion_shouldReturnOutOfScopeWithoutPendingAction() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "今天天气怎么样"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("会议") || result.getAssistantText().contains("系统"));
        assertEquals(null, result.getPendingAction());
    }

    @Test
    void message_todayMeetingListPhrase_shouldUseReservationListIntent() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-05-14 14:00:00", "2026-05-14 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我今天的会议有哪些"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("今天"));
        assertTrue(result.getAssistantText().contains("项目周会"));
    }

    @Test
    void message_tomorrowMeetingListPhrase_shouldUseReservationListIntent() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-05-15 14:00:00", "2026-05-15 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天的会议有哪些"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("明天"));
        assertTrue(result.getAssistantText().contains("项目周会"));
    }

    @Test
    void message_thisWeekReservations_shouldUseListIntentNotCreate() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-05-15 14:00:00", "2026-05-15 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我本周的预约"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("本周"));
        assertTrue(result.getAssistantText().contains("项目周会"));
    }

    @Test
    void message_reservationList_shouldDisplayEveryReturnedReservation() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        activeReservation(9001L, "会议1", "2026-05-15 09:00:00", "2026-05-15 10:00:00", "会议室1"),
                        activeReservation(9002L, "会议2", "2026-05-15 10:00:00", "2026-05-15 11:00:00", "会议室2"),
                        activeReservation(9003L, "会议3", "2026-05-15 11:00:00", "2026-05-15 12:00:00", "会议室3"),
                        activeReservation(9004L, "会议4", "2026-05-15 14:00:00", "2026-05-15 15:00:00", "会议室4"),
                        activeReservation(9005L, "会议5", "2026-05-15 15:00:00", "2026-05-15 16:00:00", "会议室5")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我本周的预约"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 5 条预约"));
        assertTrue(result.getAssistantText().contains("会议1"));
        assertTrue(result.getAssistantText().contains("会议2"));
        assertTrue(result.getAssistantText().contains("会议3"));
        assertTrue(result.getAssistantText().contains("会议4"));
        assertTrue(result.getAssistantText().contains("会议5"));
    }

    @Test
    void message_reservationList_shouldHideUnavailableStatuses() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "项目周会", "2026-05-14 14:00:00", "2026-05-14 15:00:00", "云杉会议室", "ACTIVE"),
                        reservationWithStatus(9002L, "异常客户演示", "2026-05-14 15:00:00", "2026-05-14 16:00:00", "潮汐会议室", "EXCEPTION"),
                        reservationWithStatus(9003L, "已驳回培训", "2026-05-14 16:00:00", "2026-05-14 17:00:00", "梧桐会议室", "REJECTED"),
                        reservationWithStatus(9004L, "待审核会议", "2026-05-14 17:00:00", "2026-05-14 18:00:00", "松柏会议室", "PENDING"),
                        reservationWithStatus(9005L, "已取消会议", "2026-05-14 18:00:00", "2026-05-14 19:00:00", "银杏会议室", "CANCELLED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "今天有哪些会"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 1 条预约"));
        assertTrue(result.getAssistantText().contains("项目周会"));
        assertFalse(result.getAssistantText().contains("异常客户演示"));
        assertFalse(result.getAssistantText().contains("已驳回培训"));
        assertFalse(result.getAssistantText().contains("待审核会议"));
        assertFalse(result.getAssistantText().contains("已取消会议"));
    }

    @Test
    void message_colloquialTomorrowMeetings_shouldIgnoreCancelledReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室", "ACTIVE"),
                        reservationWithStatus(9002L, "客户沟通会", "2026-04-19 16:00:00", "2026-04-19 17:00:00", "潮汐会议室", "CANCELLED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 1 条预约"));
        assertTrue(result.getAssistantText().contains("项目周会"));
        assertFalse(result.getAssistantText().contains("客户沟通会"));
    }

    @Test
    void message_reservationDetail_shouldIgnoreCancelledCandidates() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "AI实测明天下午会议", "2026-05-15 15:00:00", "2026-05-15 16:00:00", "云杉会议室", "ACTIVE"),
                        reservationWithStatus(9002L, "旧的明天下午会议", "2026-05-15 16:00:00", "2026-05-15 17:00:00", "潮汐会议室", "CANCELLED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我明天下午那个会的详情"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("AI实测明天下午会议"));
        assertFalse(result.getAssistantText().contains("旧的明天下午会议"));
    }

    @Test
    void message_listShouldLabelNonActiveReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "复盘会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室", "ENDED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("已结束"));
    }

    @Test
    void message_tomorrowReservationQuery_shouldUseReservationListIntent() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有预约吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 1 条预约"));
        assertTrue(result.getAssistantText().contains("项目周会"));
    }

    @Test
    void message_vagueRequest_shouldReturnCollectClarification() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我看看明天的"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("具体"));
    }

    @Test
    void message_contextReservationReference_shouldReuseLastReservation() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO reservation = activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室");
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(reservation));
        when(reservationService.myReservationDetail(9001L, 1L)).thenReturn(reservation);

        AiAssistantTurnVO detailTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我明天下午那个会的详情"));
        assertEquals("reply", detailTurn.getStage());

        AiAssistantTurnVO cancelTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "把那个会撤了，原因需求取消"));
        assertEquals("confirm", cancelTurn.getStage());
        assertEquals("reservations.cancel", cancelTurn.getPendingAction().getActionType());
    }

    @Test
    void message_afterReservationDetail_cancelThisMeeting_shouldUseReservationContext() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO reservation = activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室");
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(reservation));
        when(reservationService.myReservationDetail(9001L, 1L)).thenReturn(reservation);

        AiAssistantTurnVO detailTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我明天下午那个会的详情"));
        assertEquals("reply", detailTurn.getStage());

        AiAssistantTurnVO cancelTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消这个会，原因需求取消"));

        assertEquals("confirm", cancelTurn.getStage());
        assertEquals("reservations.cancel", cancelTurn.getPendingAction().getActionType());
    }

    @Test
    void message_afterRoomDetail_cancelThisRoom_shouldStillClarify() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        RoomPageItemVO room = room(101L, "云杉会议室", "A101", "1号楼", 12);
        when(roomService.pageRooms(1, 20, null, null, null, null, null)).thenReturn(roomPage(List.of(room)));
        when(roomService.userDetailById(101L)).thenReturn(room);

        AiAssistantTurnVO detailTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看云杉会议室详情"));
        assertEquals("reply", detailTurn.getStage());

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消这个会议室"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("取消预约") || result.getAssistantText().contains("放弃当前选择"));
        assertEquals(null, result.getPendingAction());
    }

    @Test
    void message_weekendQuery_shouldUseResolvedWeekendWindow() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "周末复盘", "2026-04-18 14:00:00", "2026-04-18 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "这周末我有会吗"));

        assertEquals("reply", result.getStage());
        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationService, times(1)).myReservations(eq(1L), startCaptor.capture(), endCaptor.capture(), eq("all"), eq(null), eq(false));
        LocalDate saturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        assertEquals(saturday + " 00:00:00", startCaptor.getValue());
        assertEquals(saturday.plusDays(2) + " 00:00:00", endCaptor.getValue());
    }

    @Test
    void message_adminPendingReservations_shouldReturnPendingList() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));
        when(reservationService.adminReservations(1, 10, null, "PENDING"))
                .thenReturn(adminReservationPage(List.of(adminReservation(9001L, "项目周会"))));

        AiAssistantTurnVO result = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "查看待审核预约"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("项目周会"));
        verify(reservationService).adminReservations(1, 10, null, "PENDING");
    }

    @Test
    void message_numericAdminPendingReservations_shouldReturnPendingList() {
        AuthUser admin = adminUserWithRole(2L, "2");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);
        when(reservationService.adminReservations(1, 10, null, "PENDING"))
                .thenReturn(adminReservationPage(List.of(adminReservation(1013L, "AI实测待通过"))));

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "有哪些待审核预约"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("AI实测待通过"));
        verify(reservationService).adminReservations(1, 10, null, "PENDING");
    }

    @Test
    void message_userPendingReservations_shouldRejectAdminAction() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看待审核预约"));

        assertEquals("error", result.getStage());
        assertTrue(result.getAssistantText().contains("管理员"));
    }

    @Test
    void message_adminApproveReservation_shouldEnterConfirm() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));

        AiAssistantTurnVO result = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "通过预约 9001"));

        assertEquals("confirm", result.getStage());
        assertNotNull(result.getPendingAction());
        assertEquals("admin.reservations.approve", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getConfirmRequired());
    }

    @Test
    void message_numericAdminApproveWithoutId_shouldCollectReservation() {
        AuthUser admin = adminUserWithRole(2L, "2");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);
        when(reservationService.adminReservations(1, 10, null, "PENDING"))
                .thenReturn(adminReservationPage(List.of(adminReservation(1013L, "AI实测待通过"))));

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "通过预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "reservationId".equals(field.getKey()) && "select".equals(field.getInputType())));
    }

    @Test
    void message_numericAdminApproveWithId_shouldEnterConfirm() {
        AuthUser admin = adminUserWithRole(2L, "2");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "通过预约 1013"));

        assertEquals("confirm", result.getStage());
        assertEquals("admin.reservations.approve", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getConfirmRequired());
    }

    @Test
    void message_adminApproveByReservationNo_shouldResolvePendingReservationId() {
        AuthUser admin = adminUserWithRole(2L, "2");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);
        AdminReservationItemVO pending = adminReservation(1064L, "AI实测按预约号通过");
        pending.setReservationNo("RSV1778752532878793");
        when(reservationService.adminReservations(1, 10, "RSV1778752532878793", "PENDING"))
                .thenReturn(adminReservationPage(List.of(pending)));

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "通过预约 RSV1778752532878793"));

        assertEquals("confirm", result.getStage());
        assertEquals("admin.reservations.approve", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getSummaryItems().stream().anyMatch(item ->
                "预约 ID".equals(item.getLabel()) && "1064".equals(item.getValue())));
    }

    @Test
    void confirm_adminApproveReservation_shouldCallAdminService() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "通过预约 9001"));
        when(reservationService.adminApproveReservation(9001L, 2L, null))
                .thenReturn(adminReservation(9001L, "项目周会"));

        AiAssistantTurnVO result = aiAssistantService.confirm(adminUser(2L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        assertEquals("success", result.getResult().getStatus());
        assertEquals("/admin/reservations", result.getResult().getDeepLink());
        verify(reservationService).adminApproveReservation(9001L, 2L, null);
    }

    @Test
    void message_adminRejectReservationWithoutReason_shouldCollectReason() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));

        AiAssistantTurnVO result = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "驳回预约 9001"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "reason".equals(field.getKey()) && "textarea".equals(field.getInputType())));
    }

    @Test
    void message_numericAdminRejectWithoutReason_shouldCollectReason() {
        AuthUser admin = adminUserWithRole(2L, "2");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "驳回预约 1014"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "reason".equals(field.getKey()) && "textarea".equals(field.getInputType())));
    }

    @Test
    void message_adminRejectReservationWithReason_shouldEnterConfirm() {
        AiAssistantTurnVO session = aiAssistantService.createSession(adminUser(2L));

        AiAssistantTurnVO result = aiAssistantService.message(adminUser(2L), messageRequest(session.getSessionId(), "驳回预约 9001，原因时间冲突"));

        assertEquals("confirm", result.getStage());
        assertEquals("admin.reservations.reject", result.getPendingAction().getActionType());
    }

    @Test
    void message_lowercaseAdminRejectWithReason_shouldEnterConfirm() {
        AuthUser admin = adminUserWithRole(2L, "admin");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "驳回预约 1014，原因时间冲突"));

        assertEquals("confirm", result.getStage());
        assertEquals("admin.reservations.reject", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getConfirmRequired());
    }

    @Test
    void message_adminRejectByReservationNoWithReason_shouldResolvePendingReservationId() {
        AuthUser admin = adminUserWithRole(2L, "admin");
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);
        AdminReservationItemVO pending = adminReservation(1065L, "AI实测按预约号驳回");
        pending.setReservationNo("RSV1778752532878794");
        when(reservationService.adminReservations(1, 10, "RSV1778752532878794", "PENDING"))
                .thenReturn(adminReservationPage(List.of(pending)));

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "驳回预约 RSV1778752532878794，原因时间冲突"));

        assertEquals("confirm", result.getStage());
        assertEquals("admin.reservations.reject", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getSummaryItems().stream().anyMatch(item ->
                "预约 ID".equals(item.getLabel()) && "1065".equals(item.getValue())));
        assertTrue(result.getPendingAction().getSummaryItems().stream().anyMatch(item ->
                "驳回原因".equals(item.getLabel()) && "时间冲突".equals(item.getValue())));
    }

    @Test
    void message_normalUserEmergencyPreempt_shouldReturnPermissionError() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "抢占会议室"));

        assertEquals("error", result.getStage());
        assertTrue(result.getAssistantText().contains("管理员"));
    }

    @Test
    void message_adminEmergencyPreemptMissingFields_shouldEnterCollectFlow() {
        AuthUser admin = adminUser(2L);
        AiAssistantTurnVO session = aiAssistantService.createSession(admin);

        AiAssistantTurnVO result = aiAssistantService.message(admin, messageRequest(session.getSessionId(), "安排紧急会议，可以抢占"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "emergencyReason".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "roomId".equals(field.getKey())));
    }


    private AiAssistantMessageRequestDTO messageRequest(String sessionId, String message) {
        AiAssistantMessageRequestDTO dto = new AiAssistantMessageRequestDTO();
        dto.setSessionId(sessionId);
        dto.setMessage(message);
        return dto;
    }

    private AuthUser currentUser(Long id) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName("用户" + id);
        user.setRole("USER");
        return user;
    }

    private AuthUser adminUser(Long id) {
        AuthUser user = currentUser(id);
        user.setRole("ADMIN");
        return user;
    }

    private AuthUser adminUserWithRole(Long id, String role) {
        AuthUser user = currentUser(id);
        user.setRole(role);
        return user;
    }

    private DashboardOverviewVO overview(Integer todayMeetings, Integer pendingCount, Integer availableRooms, Integer utilizationRate) {
        DashboardOverviewSummaryVO summary = new DashboardOverviewSummaryVO();
        summary.setTodayMeetingCount(todayMeetings);
        summary.setPendingCount(pendingCount);
        summary.setAvailableRoomCount(availableRooms);
        summary.setUtilizationRate(utilizationRate);
        DashboardOverviewVO overview = new DashboardOverviewVO();
        overview.setSummary(summary);
        overview.setTodaySchedules(List.of());
        return overview;
    }

    private MyReservationVO activeReservation(Long id, String title, String startTime, String endTime, String roomName) {
        return reservationWithStatus(id, title, startTime, endTime, roomName, "ACTIVE");
    }

    private MyReservationVO reservationWithStatus(Long id, String title, String startTime, String endTime, String roomName, String status) {
        MyReservationVO vo = new MyReservationVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setRoomName(roomName);
        vo.setRoomId(101L);
        vo.setStatus(status);
        vo.setCanCancel(Boolean.TRUE);
        vo.setCanEdit(Boolean.TRUE);
        return vo;
    }

    private UserOptionVO userOption(Long id, String username, String displayName) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(displayName);
        vo.setDisplayName(displayName + " (" + username + ")");
        return vo;
    }

    private AdminReservationPageVO adminReservationPage(List<AdminReservationItemVO> reservations) {
        AdminReservationPageVO page = new AdminReservationPageVO();
        page.setList(reservations);
        page.setTotal((long) reservations.size());
        return page;
    }

    private AdminReservationItemVO adminReservation(Long id, String title) {
        AdminReservationItemVO vo = new AdminReservationItemVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setRoomName("云杉会议室");
        vo.setOrganizerName("张三");
        vo.setStartTime("2026-05-13 14:00:00");
        vo.setEndTime("2026-05-13 15:00:00");
        vo.setStatus("PENDING");
        return vo;
    }

    private CalendarEventVO calendarEvent(Long id, String title, String startTime, String endTime, String roomName) {
        CalendarEventVO vo = new CalendarEventVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setRoomName(roomName);
        vo.setStatus("ACTIVE");
        return vo;
    }

    private ReservationRecommendationVO recommendation(List<ReservationRecommendationItemVO> items) {
        ReservationRecommendationVO vo = new ReservationRecommendationVO();
        vo.setRecommendations(items);
        return vo;
    }

    private ReservationRecommendationItemVO recommendationItem(Long roomId, String roomName, String location, Integer capacity) {
        ReservationRecommendationItemVO vo = new ReservationRecommendationItemVO();
        vo.setRoomId(roomId);
        vo.setRoomName(roomName);
        vo.setLocation(location);
        vo.setCapacity(capacity);
        return vo;
    }

    private RoomPageDataVO roomPage(List<RoomPageItemVO> rooms) {
        RoomPageDataVO page = new RoomPageDataVO();
        page.setList(rooms);
        page.setTotal((long) rooms.size());
        return page;
    }

    private RoomPageItemVO room(Long id, String name, String roomCode, String location, Integer capacity) {
        RoomPageItemVO vo = new RoomPageItemVO();
        vo.setId(id);
        vo.setName(name);
        vo.setRoomCode(roomCode);
        vo.setLocation(location);
        vo.setCapacity(capacity);
        vo.setStatus("AVAILABLE");
        vo.setDescription("多媒体会议室");
        vo.setDeviceBindingSummary("投影、白板");
        return vo;
    }
}
