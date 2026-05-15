package com.llf.assistant;

import com.llf.auth.AuthUser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AiAssistantToolRegistry {

    private final Map<String, AiAssistantToolDefinition> toolMap = new LinkedHashMap<>();

    public AiAssistantToolRegistry(List<AiAssistantActionHandler> handlers) {
        Map<String, AiAssistantActionHandler> handlerMap = new LinkedHashMap<>();
        for (AiAssistantActionHandler handler : handlers) {
            for (String actionType : handler.supportedActionTypes()) {
                handlerMap.put(actionType, handler);
            }
        }
        registerDefinitions(handlerMap);
    }

    public AiAssistantToolDefinition get(String toolName) {
        return toolName == null ? null : toolMap.get(toolName);
    }

    public AiAssistantToolDefinition require(String toolName) {
        AiAssistantToolDefinition tool = get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("unknown assistant tool: " + toolName);
        }
        return tool;
    }

    public Set<String> toolNames() {
        return new LinkedHashSet<>(toolMap.keySet());
    }

    public String detectToolName(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "待审核预约", "待审批预约", "待处理预约")) {
            return "admin.reservations.pending";
        }
        if (containsAny(normalized, "通过预约", "批准预约", "同意预约", "审核通过")) {
            return "admin.reservations.approve";
        }
        if (containsAny(normalized, "驳回预约", "拒绝预约", "审核驳回")) {
            return "admin.reservations.reject";
        }
        if (containsAny(normalized, "抢占", "紧急会议", "紧急预约")) {
            return "admin.emergency_reservations.confirm";
        }
        if (containsAny(normalized, "明天有哪些会", "明天有什么安排", "明天有哪些预约", "我明天有会吗", "明天有会吗", "我明天的会议有哪些")) {
            return "reservations.list";
        }
        if (containsAny(normalized, "今天有哪些会", "今天有什么安排", "今天有哪些预约", "我今天有会吗", "今天有会吗", "我今天的会议有哪些")) {
            return "reservations.list";
        }
        if (containsAny(normalized, "今日安排", "今天安排", "今日日程", "今天日程")) {
            return "overview.todaySchedule.query";
        }
        if (containsAny(normalized, "概览", "概况", "统计", "摘要")) {
            return "overview.summary.query";
        }
        if (containsAny(normalized, "删除预约", "删除我的", "取消预约", "取消我的", "撤销预约", "删掉预约")) {
            return "reservations.cancel";
        }
        if (containsAny(normalized, "取消", "删除", "撤销") && containsAny(normalized, "预约", "会议", "会")) {
            return "reservations.cancel";
        }
        if (containsAny(normalized, "修改", "调整", "改到", "改成") && containsAny(normalized, "预约", "会议", "会")) {
            return "reservations.update";
        }
        if (containsAny(normalized, "评价", "点评", "打分")) {
            return "reservations.review";
        }
        if (containsAny(normalized, "创建预约", "创建一个预约", "帮我预约", "帮我创建", "新增预约", "安排会议")) {
            return "reservations.create";
        }
        if (containsAny(normalized, "预约详情", "这条预约", "这场预约", "详情")) {
            return "reservations.detail";
        }
        if (containsAny(normalized, "我的预约", "本周预约", "本周的预约", "这周的预约", "查看预约", "预约列表", "有预约", "有没有预约", "是否有预约", "有会", "有哪些会", "哪些会")) {
            return "reservations.list";
        }
        if (containsAny(normalized, "会议室详情", "查看会议室", "会议室信息")) {
            return "rooms.detail";
        }
        if (containsAny(normalized, "会议室", "空闲", "可用", "找会议室")) {
            return "rooms.search";
        }
        if (containsAny(normalized, "日历", "排期", "日程")) {
            return "calendar.query";
        }
        return null;
    }

    public boolean hasPermission(AiAssistantToolDefinition tool, AuthUser user) {
        if (tool == null) {
            return false;
        }
        if ("ADMIN".equals(tool.getRequiredPermission())) {
            return isAdminRole(user == null ? null : user.getRole());
        }
        return user != null && user.getId() != null;
    }

    private boolean isAdminRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String value = role.trim();
        return "ADMIN".equalsIgnoreCase(value) || "2".equals(value) || "admin".equalsIgnoreCase(value);
    }

    private void registerDefinitions(Map<String, AiAssistantActionHandler> handlerMap) {
        register(handlerMap, "overview.summary.query", "查询系统概览", "overview", "read", "USER", false, List.of(), "query_result");
        register(handlerMap, "overview.todaySchedule.query", "查询今日安排", "overview", "read", "USER", false, List.of(), "query_result");
        register(handlerMap, "calendar.query", "查询日历安排", "calendar", "read", "USER", false, List.of("dateFrom", "dateTo"), "query_result");
        register(handlerMap, "rooms.search", "查询会议室", "rooms", "read", "USER", false, List.of(), "query_result");
        register(handlerMap, "rooms.detail", "查询会议室详情", "rooms", "read", "USER", false, List.of("roomId"), "query_result");
        register(handlerMap, "reservations.list", "查询我的预约", "reservations", "read", "USER", false, List.of("dateFrom", "dateTo"), "query_result");
        register(handlerMap, "reservations.detail", "查询预约详情", "reservations", "read", "USER", false, List.of("reservationId"), "query_result");
        register(handlerMap, "reservations.create", "创建预约", "reservations", "write", "USER", true, List.of("title", "meetingDate", "startClock", "endClock", "roomId"), "confirmation");
        register(handlerMap, "reservations.update", "修改预约", "reservations", "write", "USER", true, List.of("reservationId"), "confirmation");
        register(handlerMap, "reservations.cancel", "取消预约", "reservations", "write", "USER", true, List.of("reservationId", "cancelReason"), "confirmation");
        register(handlerMap, "reservations.review", "预约评价", "reservations", "write", "USER", true, List.of("reservationId", "rating"), "confirmation");
        register(handlerMap, "admin.reservations.pending", "查询待审核预约", "admin", "read", "ADMIN", false, List.of(), "query_result");
        register(handlerMap, "admin.reservations.approve", "通过预约审核", "admin", "write", "ADMIN", true, List.of("reservationId"), "confirmation");
        register(handlerMap, "admin.reservations.reject", "驳回预约审核", "admin", "write", "ADMIN", true, List.of("reservationId", "reason"), "confirmation");
        register(handlerMap, "admin.emergency_reservations.preview", "预览紧急会议抢占调配", "admin", "read", "ADMIN", false, List.of("title", "meetingDate", "startClock", "endClock", "roomId", "emergencyReason"), "query_result");
        register(handlerMap, "admin.emergency_reservations.confirm", "创建紧急会议并执行抢占调配", "admin", "write", "ADMIN", true, List.of("title", "meetingDate", "startClock", "endClock", "roomId", "emergencyReason"), "confirmation");
    }

    private void register(Map<String, AiAssistantActionHandler> handlerMap,
                          String toolName,
                          String description,
                          String category,
                          String operationType,
                          String requiredPermission,
                          boolean confirmRequired,
                          List<String> requiredFields,
                          String resultCardType) {
        AiAssistantActionHandler handler = handlerMap.get(toolName);
        if (handler == null) {
            return;
        }
        toolMap.put(toolName, new AiAssistantToolDefinition(
                toolName,
                description,
                category,
                operationType,
                requiredPermission,
                confirmRequired,
                requiredFields,
                handler,
                resultCardType
        ));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
