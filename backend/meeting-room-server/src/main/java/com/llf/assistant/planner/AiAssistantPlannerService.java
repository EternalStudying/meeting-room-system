package com.llf.assistant.planner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantToolDefinition;
import com.llf.assistant.AiAssistantToolRegistry;
import com.llf.assistant.semantic.AiAssistantIntentFields;
import com.llf.assistant.semantic.AiAssistantIntentLlmClient;
import com.llf.assistant.semantic.AiAssistantIntentLlmRequest;
import com.llf.assistant.semantic.AiAssistantIntentParseResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiAssistantPlannerService {

    private static final String PROMPT_RESOURCE = "ai/assistant-planner-v2-prompt.md";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final AiAssistantIntentLlmClient llmClient;
    private final AiAssistantPlanValidator planValidator;
    private final AiAssistantToolRegistry toolRegistry;
    private final String plannerPrompt;

    public AiAssistantPlannerService(AiAssistantIntentLlmClient llmClient,
                                     AiAssistantPlanValidator planValidator,
                                     AiAssistantToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.planValidator = planValidator;
        this.toolRegistry = toolRegistry;
        this.plannerPrompt = loadPrompt();
    }

    public AiAssistantIntentParseResult parse(String originalText,
                                              String normalizedText,
                                              AiAssistantSessionStore.Session session) {
        String json = llmClient.parseIntentJson(new AiAssistantIntentLlmRequest(
                originalText,
                normalizedText,
                session == null ? null : session.getCurrentActionType(),
                session == null ? List.of() : List.copyOf(session.getRecentMessages()),
                buildPlannerContext()
        ));
        if (json == null || json.isBlank()) {
            return null;
        }
        AiAssistantPlanValidator.ValidationResult validation = planValidator.validateJson(json);
        if (!validation.valid() || !validation.executable()) {
            return null;
        }
        try {
            AiAssistantPlan plan = objectMapper.readValue(json, AiAssistantPlan.class);
            return toParseResult(plan, normalizedText);
        } catch (Exception e) {
            return null;
        }
    }

    private AiAssistantIntentParseResult toParseResult(AiAssistantPlan plan, String normalizedText) {
        AiAssistantIntentParseResult result = new AiAssistantIntentParseResult();
        result.setNormalizedText(normalizedText);
        result.setActionType(plan.getToolName());
        result.setConfidence(plan.getConfidence() == null ? 0D : plan.getConfidence());
        result.setNeedClarification(plan.getMissingFields() != null && !plan.getMissingFields().isEmpty());
        result.setClarificationReason(plan.getAmbiguity());
        result.setFields(toIntentFields(plan.getFields()));
        return result;
    }

    private AiAssistantIntentFields toIntentFields(AiAssistantPlanFields fields) {
        AiAssistantIntentFields target = new AiAssistantIntentFields();
        if (fields == null) {
            return target;
        }
        target.setReservationId(fields.getReservationId());
        target.setRoomId(fields.getRoomId());
        target.setRoomName(fields.getRoomName());
        target.setTitle(fields.getTitle());
        target.setMeetingDate(fields.getMeetingDate());
        target.setDateFrom(fields.getDateFrom());
        target.setDateTo(fields.getDateTo());
        target.setStartClock(fields.getStartClock());
        target.setEndClock(fields.getEndClock());
        target.setAttendees(fields.getAttendees());
        target.setRating(fields.getRating());
        target.setContent(fields.getContent());
        target.setDeviceRequirements(fields.getDeviceRequirements());
        target.setTargetScope(fields.getTargetScope());
        target.setTimeRangeLabel(fields.getTimeRangeLabel());
        target.setRelativeTarget(fields.getRelativeTarget());
        target.setMutationHint(fields.getMutationHint());
        target.setTimeShiftMinutes(fields.getTimeShiftMinutes());
        target.setQuantityHint(fields.getQuantityHint());
        target.setParticipantUserIds(fields.getParticipantUserIds());
        target.setAllowPreempt(fields.getAllowPreempt());
        target.setEmergencyReason(fields.getEmergencyReason());
        return target;
    }

    private String buildPlannerContext() {
        return plannerPrompt + "\n\nRegistered tools:\n" + toolSummary();
    }

    private String toolSummary() {
        Set<String> toolNames = toolRegistry.toolNames();
        return toolNames.stream()
                .map(toolRegistry::get)
                .map(this::formatTool)
                .collect(Collectors.joining("\n"));
    }

    private String formatTool(AiAssistantToolDefinition tool) {
        return "- " + tool.getToolName()
                + " | " + tool.getDescription()
                + " | operationType=" + tool.getOperationType()
                + " | permission=" + tool.getRequiredPermission()
                + " | requiredFields=" + tool.getRequiredFields();
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource(PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load planner prompt", e);
        }
    }
}
