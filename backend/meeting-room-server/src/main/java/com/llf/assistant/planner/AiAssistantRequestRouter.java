package com.llf.assistant.planner;

import org.springframework.stereotype.Component;

@Component
public class AiAssistantRequestRouter {

    public RouteType route(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return RouteType.CLARIFICATION;
        }
        if (containsAny(normalizedText, "天气", "笑话", "新闻", "股票", "外卖")) {
            return RouteType.OUT_OF_SCOPE;
        }
        if (normalizedText.contains("取消这个会议室")) {
            return RouteType.CLARIFICATION;
        }
        if (containsAny(normalizedText, "概览", "概况", "统计", "摘要")) {
            return RouteType.OPERATION;
        }
        if (containsAny(normalizedText, "怎么", "如何", "规则", "什么意思", "需要", "帮助")) {
            return RouteType.KNOWLEDGE;
        }
        return RouteType.OPERATION;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public enum RouteType {
        OPERATION,
        KNOWLEDGE,
        CLARIFICATION,
        OUT_OF_SCOPE
    }
}
