package com.llf.assistant.planner;

import lombok.Data;

import java.util.List;

@Data
public class AiAssistantPlan {
    private String intentType;
    private String toolName;
    private Double confidence;
    private AiAssistantPlanFields fields = new AiAssistantPlanFields();
    private List<String> missingFields = List.of();
    private String ambiguity;
    private String reason;
}
