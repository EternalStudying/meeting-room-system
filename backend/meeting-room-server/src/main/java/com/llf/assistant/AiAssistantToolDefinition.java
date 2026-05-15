package com.llf.assistant;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AiAssistantToolDefinition {
    private final String toolName;
    private final String description;
    private final String category;
    private final String operationType;
    private final String requiredPermission;
    private final boolean confirmRequired;
    private final List<String> requiredFields;
    private final AiAssistantActionHandler handler;
    private final String resultCardType;

    public AiAssistantToolDefinition(String toolName,
                                     String description,
                                     String category,
                                     String operationType,
                                     String requiredPermission,
                                     boolean confirmRequired,
                                     List<String> requiredFields,
                                     AiAssistantActionHandler handler,
                                     String resultCardType) {
        this.toolName = toolName;
        this.description = description;
        this.category = category;
        this.operationType = operationType;
        this.requiredPermission = requiredPermission;
        this.confirmRequired = confirmRequired;
        this.requiredFields = requiredFields == null ? List.of() : new ArrayList<>(requiredFields);
        this.handler = handler;
        this.resultCardType = resultCardType;
    }

    public boolean isWrite() {
        return "write".equals(operationType);
    }
}
