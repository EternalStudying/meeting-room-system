package com.llf.assistant.planner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.assistant.AiAssistantToolDefinition;
import com.llf.assistant.AiAssistantToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AiAssistantPlanValidator {

    private static final Set<String> INTENT_TYPES = Set.of("operation", "knowledge", "mixed", "clarification", "out_of_scope");
    private static final double EXECUTABLE_CONFIDENCE = 0.70D;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final AiAssistantToolRegistry toolRegistry;

    public AiAssistantPlanValidator(AiAssistantToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ValidationResult validateJson(String json) {
        if (json == null || json.isBlank()) {
            return ValidationResult.invalid("empty json");
        }
        try {
            return validate(objectMapper.readValue(json, AiAssistantPlan.class));
        } catch (Exception e) {
            return ValidationResult.invalid("json schema invalid: " + e.getMessage());
        }
    }

    public ValidationResult validate(AiAssistantPlan plan) {
        if (plan == null) {
            return ValidationResult.invalid("plan is null");
        }
        normalize(plan);
        if (!INTENT_TYPES.contains(plan.getIntentType())) {
            return ValidationResult.invalid("intentType invalid");
        }
        if (plan.getConfidence() < 0D || plan.getConfidence() > 1D) {
            return ValidationResult.invalid("confidence invalid");
        }
        if (!"operation".equals(plan.getIntentType())) {
            if (!isBlank(plan.getToolName())) {
                return ValidationResult.invalid("toolName must be empty for non-operation intent");
            }
            return new ValidationResult(true, false, "non-operation intent");
        }
        if (isBlank(plan.getToolName())) {
            return ValidationResult.invalid("toolName required");
        }
        AiAssistantToolDefinition tool = toolRegistry.get(plan.getToolName());
        if (tool == null) {
            return ValidationResult.invalid("toolName not registered");
        }
        if (tool.isWrite() && !isBlank(plan.getAmbiguity())) {
            return ValidationResult.invalid("ambiguity blocks write operation");
        }
        if (plan.getConfidence() < EXECUTABLE_CONFIDENCE) {
            return new ValidationResult(false, false, "confidence below executable threshold");
        }
        return new ValidationResult(true, true, "ok");
    }

    private void normalize(AiAssistantPlan plan) {
        if (plan.getConfidence() == null) {
            plan.setConfidence(0D);
        }
        if (plan.getFields() == null) {
            plan.setFields(new AiAssistantPlanFields());
        }
        if (plan.getMissingFields() == null) {
            plan.setMissingFields(List.of());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ValidationResult(boolean valid, boolean executable, String reason) {
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, false, reason);
        }
    }
}
