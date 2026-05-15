package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantHelper;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.auth.AuthUser;
import com.llf.dto.admin.reservation.EmergencyReservationRequestDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.service.EmergencyReservationService;
import com.llf.service.RoomService;
import com.llf.service.UserService;
import com.llf.vo.admin.reservation.EmergencyReservationActionVO;
import com.llf.vo.admin.reservation.EmergencyReservationConfirmVO;
import com.llf.vo.admin.reservation.EmergencyReservationPreviewVO;
import com.llf.vo.assistant.AiAssistantMissingFieldVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.assistant.AiAssistantSummaryItemVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AdminEmergencyReservationAssistantActionHandler implements AiAssistantActionHandler {

    private static final String ACTION_PREVIEW = "admin.emergency_reservations.preview";
    private static final String ACTION_CONFIRM = "admin.emergency_reservations.confirm";

    private final EmergencyReservationService emergencyReservationService;

    public AdminEmergencyReservationAssistantActionHandler(EmergencyReservationService emergencyReservationService,
                                                           RoomService roomService,
                                                           UserService userService) {
        this.emergencyReservationService = emergencyReservationService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of(ACTION_PREVIEW, ACTION_CONFIRM);
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        if (!isAdmin(currentUser)) {
            return AiAssistantActionPlan.error("该操作仅管理员可用。", defaultSuggestions(), errorResult("无管理员权限"));
        }
        Map<String, Object> draft = session.getDraft();
        mergeMessageHints(draft, message);
        List<AiAssistantMissingFieldVO> missingFields = buildMissingFields(draft);
        if (!missingFields.isEmpty()) {
            return AiAssistantActionPlan.collect(
                    "创建紧急会议前，请补齐会议时间、会议室和紧急原因。",
                    List.of("补充紧急原因", "选择会议室"),
                    missingFields
            );
        }

        EmergencyReservationPreviewVO preview = emergencyReservationService.preview(toDto(draft), currentUser.getId());
        if (!Boolean.TRUE.equals(preview.getCanExecute())) {
            return AiAssistantActionPlan.error(preview.getMessage(), defaultSuggestions(), errorResult("存在冲突"));
        }
        if (ACTION_PREVIEW.equals(actionType)) {
            return AiAssistantActionPlan.reply(preview.getMessage(), List.of("确认抢占调配", "取消本次操作"));
        }
        return AiAssistantActionPlan.confirm(
                "已生成抢占调配预览，请确认是否创建紧急会议并执行调配。",
                List.of("确认执行", "取消本次操作"),
                "创建紧急会议",
                buildSummary(preview),
                Map.copyOf(draft)
        );
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        if (!isAdmin(currentUser)) {
            return AiAssistantExecutionResult.error("该操作仅管理员可用。", "无管理员权限", List.of());
        }
        if (!ACTION_CONFIRM.equals(actionType)) {
            return AiAssistantExecutionResult.error("这个动作不需要执行。", "动作无需执行", List.of());
        }
        EmergencyReservationConfirmVO result = emergencyReservationService.confirm(toDto(params), currentUser.getId());
        return AiAssistantExecutionResult.success(
                "紧急会议已创建并完成抢占调配。",
                "紧急会议已创建",
                List.of(
                        AiAssistantHelper.summary("预约编号", result.getReservationNo()),
                        AiAssistantHelper.summary("状态", result.getStatus())
                ),
                "/admin/reservations"
        );
    }

    private List<AiAssistantMissingFieldVO> buildMissingFields(Map<String, Object> draft) {
        List<AiAssistantMissingFieldVO> fields = new ArrayList<>();
        addMissing(fields, draft, "title", "会议主题", "text", "例如：核心客户事故复盘");
        addMissing(fields, draft, "meetingDate", "会议日期", "date", "选择日期");
        addMissing(fields, draft, "startClock", "开始时间", "time", "例如：15:00");
        addMissing(fields, draft, "endClock", "结束时间", "time", "例如：16:00");
        addMissing(fields, draft, "roomId", "会议室", "number", "请输入会议室 ID");
        addMissing(fields, draft, "emergencyReason", "紧急原因", "textarea", "说明为什么需要抢占调配");
        return fields;
    }

    private void addMissing(List<AiAssistantMissingFieldVO> fields,
                            Map<String, Object> draft,
                            String key,
                            String label,
                            String inputType,
                            String placeholder) {
        if (AiAssistantHelper.stringValue(draft, key) == null) {
            fields.add(AiAssistantHelper.field(key, label, inputType, true, placeholder, draft.get(key), List.of()));
        }
    }

    private void mergeMessageHints(Map<String, Object> draft, String message) {
        if (message == null) {
            return;
        }
        if (containsAny(message, "抢占", "可以抢占", "允许抢占")) {
            draft.put("allowPreempt", true);
        }
        if (AiAssistantHelper.stringValue(draft, "title") == null) {
            if (message.contains("复盘")) {
                draft.put("title", "复盘会");
            } else if (message.contains("评审")) {
                draft.put("title", "评审会");
            }
        }
    }

    private EmergencyReservationRequestDTO toDto(Map<String, Object> values) {
        EmergencyReservationRequestDTO dto = new EmergencyReservationRequestDTO();
        dto.setTitle(AiAssistantHelper.stringValue(values, "title"));
        dto.setMeetingDate(AiAssistantHelper.stringValue(values, "meetingDate"));
        dto.setStartClock(AiAssistantHelper.stringValue(values, "startClock"));
        dto.setEndClock(AiAssistantHelper.stringValue(values, "endClock"));
        dto.setRoomId(AiAssistantHelper.longValue(values, "roomId"));
        Integer attendees = AiAssistantHelper.integerValue(values, "attendees");
        dto.setAttendees(attendees == null || attendees < 1 ? resolveAttendees(values) : attendees);
        dto.setAllowPreempt(resolveBoolean(values.get("allowPreempt")));
        dto.setEmergencyReason(AiAssistantHelper.stringValue(values, "emergencyReason"));
        dto.setRemark(AiAssistantHelper.stringValue(values, "remark"));
        dto.setParticipantUserIds(AiAssistantHelper.longListValue(values, "participantUserIds"));
        dto.setDeviceRequirements(deviceRequirementsValue(values));
        return dto;
    }

    private Integer resolveAttendees(Map<String, Object> values) {
        List<Long> participantIds = AiAssistantHelper.longListValue(values, "participantUserIds");
        return participantIds.isEmpty() ? 1 : participantIds.size() + 1;
    }

    private Boolean resolveBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return Boolean.FALSE;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private List<ReservationDeviceRequirementDTO> deviceRequirementsValue(Map<String, Object> values) {
        Object rawValue = values == null ? null : values.get("deviceRequirements");
        if (!(rawValue instanceof List<?> listValue)) {
            return List.of();
        }
        List<ReservationDeviceRequirementDTO> requirements = new ArrayList<>();
        for (Object item : listValue) {
            if (!(item instanceof Map<?, ?> mapValue)) {
                continue;
            }
            Long deviceId = toLong(mapValue.get("deviceId"));
            Integer quantity = toInteger(mapValue.get("quantity"));
            if (deviceId == null || quantity == null || quantity < 1) {
                continue;
            }
            ReservationDeviceRequirementDTO requirement = new ReservationDeviceRequirementDTO();
            requirement.setDeviceId(deviceId);
            requirement.setQuantity(quantity);
            requirements.add(requirement);
        }
        return requirements;
    }

    private List<AiAssistantSummaryItemVO> buildSummary(EmergencyReservationPreviewVO preview) {
        List<AiAssistantSummaryItemVO> summary = new ArrayList<>();
        summary.add(AiAssistantHelper.summary("会议主题", preview.getEmergencySummary().getTitle()));
        summary.add(AiAssistantHelper.summary("会议室", preview.getEmergencySummary().getRoomName()));
        summary.add(AiAssistantHelper.summary("时间", preview.getEmergencySummary().getStartTime() + " - " + preview.getEmergencySummary().getEndTime()));
        summary.add(AiAssistantHelper.summary("冲突预约", String.valueOf(preview.getConflicts().size())));
        long moveCount = preview.getActions().stream().filter(item -> "MOVE_ROOM".equals(item.getActionType())).count();
        long cancelCount = preview.getActions().stream().filter(item -> "CANCEL".equals(item.getActionType())).count();
        summary.add(AiAssistantHelper.summary("调配动作", "换会议室 " + moveCount + " 条，取消 " + cancelCount + " 条"));
        return summary;
    }

    private Long toLong(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAdmin(AuthUser user) {
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return false;
        }
        String role = user.getRole().trim();
        return "ADMIN".equalsIgnoreCase(role) || "2".equals(role) || "admin".equalsIgnoreCase(role);
    }

    private AiAssistantResultVO errorResult(String title) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle(title);
        return result;
    }

    private List<String> defaultSuggestions() {
        return List.of("查看待审核预约", "查看预约日历");
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
