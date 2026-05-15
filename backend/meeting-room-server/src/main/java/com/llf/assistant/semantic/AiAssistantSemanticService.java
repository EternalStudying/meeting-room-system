package com.llf.assistant.semantic;

import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.planner.AiAssistantPlannerService;
import com.llf.vo.assistant.AiAssistantTurnVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiAssistantSemanticService {

    private final AiAssistantTextNormalizer textNormalizer;
    private final AiAssistantTimeResolver timeResolver;
    private final AiAssistantIntentParser intentParser;
    private final AiAssistantIntentSchemaValidator schemaValidator;
    private final AiAssistantReferenceResolver referenceResolver;
    private final AiAssistantPlannerService plannerService;

    public AiAssistantSemanticService(AiAssistantTextNormalizer textNormalizer,
                                      AiAssistantTimeResolver timeResolver,
                                      AiAssistantIntentParser intentParser,
                                      AiAssistantIntentSchemaValidator schemaValidator,
                                      AiAssistantReferenceResolver referenceResolver,
                                      AiAssistantPlannerService plannerService) {
        this.textNormalizer = textNormalizer;
        this.timeResolver = timeResolver;
        this.intentParser = intentParser;
        this.schemaValidator = schemaValidator;
        this.referenceResolver = referenceResolver;
        this.plannerService = plannerService;
    }

    public AiAssistantIntentParseResult parse(String userText, AiAssistantSessionStore.Session session) {
        String normalizedText = textNormalizer.normalize(userText);
        AiAssistantIntentParseResult llmResult = plannerService.parse(userText, normalizedText, session);
        if (llmResult != null) {
            timeResolver.resolve(userText, llmResult.getNormalizedText(), llmResult.getFields());
            referenceResolver.resolve(llmResult, session);
            if (schemaValidator.isValid(llmResult)
                    && llmResult.getConfidence() >= 0.70D
                    && isToolConsistentWithRequest(normalizedText, llmResult.getActionType())) {
                return llmResult;
            }
        }

        AiAssistantIntentParseResult ruleResult = intentParser.parseByRules(userText, normalizedText);
        timeResolver.resolve(userText, normalizedText, ruleResult.getFields());
        referenceResolver.resolve(ruleResult, session);
        return ruleResult;
    }

    public Map<String, Object> toDraftMap(AiAssistantIntentParseResult parseResult) {
        return parseResult == null || parseResult.getFields() == null ? Map.of() : parseResult.getFields().toMap();
    }

    private boolean isToolConsistentWithRequest(String normalizedText, String actionType) {
        String source = normalizedText == null ? "" : normalizedText;
        if (isVagueTemporalRequest(source)) {
            return false;
        }
        if (isOverviewQuery(source)) {
            return "overview.summary.query".equals(actionType);
        }
        if (isRoomAvailabilityQuery(source)) {
            return "rooms.search".equals(actionType);
        }
        if (isMeetingListQuery(source) || isScopedMeetingListQuery(source)) {
            return "reservations.list".equals(actionType);
        }
        return true;
    }

    private boolean isVagueTemporalRequest(String source) {
        if (!containsAny(source, "今天的", "明天的", "后天的", "本周的", "这周的", "下周的")) {
            return false;
        }
        if (!containsAny(source, "看看", "看下", "看一下", "查下", "查一下", "查询")) {
            return false;
        }
        return !containsAny(source,
                "概览", "会议室", "会议", "预约", "日历", "日程", "安排", "详情",
                "创建", "修改", "取消", "评价", "通过", "驳回", "审核");
    }

    private boolean isOverviewQuery(String source) {
        return containsAny(source, "概览", "概况", "统计", "摘要");
    }

    private boolean isRoomAvailabilityQuery(String source) {
        return source.contains("会议室")
                && containsAny(source, "可用", "可以用", "能用", "空闲", "可预约", "推荐", "找");
    }

    private boolean isMeetingListQuery(String source) {
        if (containsAny(source, "会议室", "日历", "日程", "安排")) {
            return false;
        }
        return containsAny(source, "有哪些会", "有哪些会议", "哪些会", "哪些会议", "有会吗", "有没有会");
    }

    private boolean isScopedMeetingListQuery(String source) {
        return containsAny(source, "我发起", "我创建", "我组织", "我参与", "我参加")
                && containsAny(source, "会议", "会", "预约");
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public AiAssistantTurnVO toClarificationTurn(String sessionId, AiAssistantIntentParseResult parseResult, List<String> suggestions) {
        AiAssistantTurnVO turn = new AiAssistantTurnVO();
        turn.setSessionId(sessionId);
        turn.setRole("assistant");
        turn.setState("collecting");
        turn.setStage("collect");
        String message = parseResult.getClarificationReason() == null ? "请再说具体一点。" : parseResult.getClarificationReason();
        turn.setMessage(message);
        turn.setAssistantText(message);
        turn.setSuggestions(suggestions == null ? List.of() : suggestions);
        return turn;
    }
}
