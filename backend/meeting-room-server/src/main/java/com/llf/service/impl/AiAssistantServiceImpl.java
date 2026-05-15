package com.llf.service.impl;

import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantToolDefinition;
import com.llf.assistant.AiAssistantToolRegistry;
import com.llf.assistant.planner.AiAssistantRequestRouter;
import com.llf.assistant.rag.AiAssistantRagService;
import com.llf.assistant.semantic.AiAssistantIntentParseResult;
import com.llf.assistant.semantic.AiAssistantSemanticService;
import com.llf.auth.AuthUser;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.result.BizException;
import com.llf.service.AiAssistantService;
import com.llf.vo.assistant.AiAssistantCardVO;
import com.llf.vo.assistant.AiAssistantPendingActionVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.assistant.AiAssistantSummaryItemVO;
import com.llf.vo.assistant.AiAssistantTurnVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final String INVALID_EXECUTION_MESSAGE = "这次待执行动作已经失效，请重新描述你的需求。";
    private static final String OUT_OF_SCOPE_MESSAGE = "我是你的任务助手，可以帮你查询概览、会议室、日历和我的预约，也可以协助创建、修改、取消预约。业务无关内容我暂时处理不了。";
    private static final String WELCOME_MESSAGE = "我是你的任务助手，可以帮你查询概览、会议室、日历和我的预约，也可以代你完成预约相关操作。";
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "帮我创建一个预约",
            "取消我明天下午的预约",
            "查看我本周的预约",
            "今天下午有哪些空闲会议室"
    );

    private final AiAssistantSessionStore sessionStore;
    private final AiAssistantToolRegistry toolRegistry;
    private final AiAssistantSemanticService semanticService;
    private final AiAssistantRequestRouter requestRouter;
    private final AiAssistantRagService ragService;

    public AiAssistantServiceImpl(AiAssistantSessionStore sessionStore,
                                  AiAssistantToolRegistry toolRegistry,
                                  AiAssistantSemanticService semanticService,
                                  AiAssistantRequestRouter requestRouter,
                                  AiAssistantRagService ragService) {
        this.sessionStore = sessionStore;
        this.toolRegistry = toolRegistry;
        this.semanticService = semanticService;
        this.requestRouter = requestRouter;
        this.ragService = ragService;
    }

    @Override
    public AiAssistantTurnVO createSession(AuthUser currentUser) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.Session session = sessionStore.create(userId);
        return baseTurn(session.getSessionId(), "reply", "idle", WELCOME_MESSAGE, DEFAULT_SUGGESTIONS, List.of(AiAssistantCardVO.text(WELCOME_MESSAGE)));
    }

    @Override
    public AiAssistantTurnVO message(AuthUser currentUser, AiAssistantMessageRequestDTO dto) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, dto == null ? null : dto.getSessionId());
        String message = dto == null || dto.getMessage() == null ? null : dto.getMessage().trim();
        Map<String, Object> fieldValues = dto == null ? null : dto.getFieldValues();

        if (message != null && !message.isBlank()) {
            sessionStore.rememberMessage(session, "user", message);
            AiAssistantRequestRouter.RouteType routeType = requestRouter.route(message);
            if (routeType == AiAssistantRequestRouter.RouteType.KNOWLEDGE) {
                AiAssistantTurnVO turn = ragTurn(session.getSessionId(), ragService.answer(message));
                sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                return turn;
            }
            if (routeType == AiAssistantRequestRouter.RouteType.OUT_OF_SCOPE) {
                AiAssistantTurnVO turn = ragTurn(session.getSessionId(), ragService.answer(message));
                sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                return turn;
            }
            AiAssistantIntentParseResult parseResult = semanticService.parse(message, session);
            if (isCancelRoomAmbiguity(parseResult.getNormalizedText())) {
                sessionStore.clearProgress(session);
                AiAssistantTurnVO turn = clarificationTurn(session.getSessionId(), "你是想取消某个预约，还是只是放弃当前选择的会议室？", DEFAULT_SUGGESTIONS);
                sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                return turn;
            }
            if (parseResult.getConfidence() < 0.5D) {
                sessionStore.clearProgress(session);
                if ("unknown".equals(parseResult.getActionType()) && !shouldClarifyUnknown(parseResult)) {
                    AiAssistantTurnVO turn = textTurn(session.getSessionId(), OUT_OF_SCOPE_MESSAGE);
                    sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                    return turn;
                }
                AiAssistantTurnVO turn = clarificationTurn(session.getSessionId(), parseResult.getClarificationReason(), DEFAULT_SUGGESTIONS);
                sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                return turn;
            }
            if (!"unknown".equals(parseResult.getActionType())) {
                sessionStore.resetForNewAction(session, parseResult.getActionType());
                sessionStore.mergeDraft(session, semanticService.toDraftMap(parseResult));
                message = parseResult.getNormalizedText();
            } else if (session.getCurrentActionType() != null) {
                sessionStore.mergeDraft(session, semanticService.toDraftMap(parseResult));
                message = parseResult.getNormalizedText();
            } else if (parseResult.isNeedClarification()) {
                sessionStore.clearProgress(session);
                AiAssistantTurnVO turn = clarificationTurn(session.getSessionId(), parseResult.getClarificationReason(), DEFAULT_SUGGESTIONS);
                sessionStore.rememberMessage(session, "assistant", turn.getMessage());
                return turn;
            } else if (session.getCurrentActionType() == null) {
                return textTurn(session.getSessionId(), OUT_OF_SCOPE_MESSAGE);
            }
        }

        if (fieldValues != null && !fieldValues.isEmpty()) {
            if (session.getCurrentActionType() == null) {
                return errorTurn(session.getSessionId(), "当前没有待补充的任务，请先告诉我你的需求。");
            }
            sessionStore.mergeDraft(session, fieldValues);
        }

        if ((message == null || message.isBlank()) && (fieldValues == null || fieldValues.isEmpty())) {
            return errorTurn(session.getSessionId(), "请提供 message 或 fieldValues。");
        }

        String actionType = session.getCurrentActionType();
        AiAssistantToolDefinition tool = toolRegistry.get(actionType);
        if (tool == null || tool.getHandler() == null) {
            return textTurn(session.getSessionId(), OUT_OF_SCOPE_MESSAGE);
        }
        if (!toolRegistry.hasPermission(tool, currentUser)) {
            sessionStore.clearProgress(session);
            return permissionDeniedTurn(session.getSessionId(), tool);
        }

        AiAssistantActionPlan plan = tool.getHandler().process(actionType, currentUser, session, message);
        session.setStage(plan.getStage());
        return toTurn(session, tool, plan);
    }

    @Override
    public AiAssistantTurnVO confirm(AuthUser currentUser, String executionId) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.PendingExecution execution = sessionStore.findValidExecution(userId, executionId);
        if (execution == null) {
            return invalidExecutionTurn();
        }

        AiAssistantToolDefinition tool = toolRegistry.get(execution.getActionType());
        if (tool == null || tool.getHandler() == null) {
            sessionStore.removeExecution(executionId);
            return invalidExecutionTurn();
        }
        if (!toolRegistry.hasPermission(tool, currentUser)) {
            sessionStore.removeExecution(executionId);
            return permissionDeniedTurn(execution.getSessionId(), tool);
        }

        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, execution.getSessionId());
        try {
            AiAssistantExecutionResult executionResult = tool.getHandler().execute(execution.getActionType(), currentUser, execution.getParams());
            audit(userId, execution, executionResult.getStatus(), executionResult.getTitle());
            sessionStore.removeExecution(executionId);
            sessionStore.clearProgress(session);
            return resultTurn(session.getSessionId(), executionResult);
        } catch (BizException e) {
            audit(userId, execution, "error", e.getMessage());
            sessionStore.removeExecution(executionId);
            sessionStore.clearProgress(session);
            return errorTurn(session.getSessionId(), e.getMessage());
        }
    }

    @Override
    public AiAssistantTurnVO cancel(AuthUser currentUser, String executionId) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.PendingExecution execution = sessionStore.findValidExecution(userId, executionId);
        if (execution == null) {
            return invalidExecutionTurn();
        }

        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, execution.getSessionId());
        sessionStore.removeExecution(executionId);
        sessionStore.clearProgress(session);

        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("cancelled");
        result.setTitle("已取消本次操作");
        result.setSummaryItems(List.of(new AiAssistantSummaryItemVO("动作", execution.getTitle())));

        AiAssistantTurnVO turn = baseTurn(session.getSessionId(), "result", "executed", "本次操作已取消。", DEFAULT_SUGGESTIONS, List.of());
        turn.setResult(result);
        turn.setCards(List.of(AiAssistantCardVO.executionResult(result, "本次操作已取消。")));
        return turn;
    }

    private AiAssistantTurnVO toTurn(AiAssistantSessionStore.Session session, AiAssistantToolDefinition tool, AiAssistantActionPlan plan) {
        if ("confirm".equals(plan.getStage())) {
            AiAssistantSessionStore.PendingExecution execution = sessionStore.saveExecution(
                    session.getUserId(),
                    session.getSessionId(),
                    tool.getToolName(),
                    plan.getParams(),
                    plan.getTitle(),
                    plan.getSummaryItems(),
                    tool.isConfirmRequired()
            );
            AiAssistantPendingActionVO pendingAction = new AiAssistantPendingActionVO();
            pendingAction.setExecutionId(execution.getExecutionId());
            pendingAction.setActionType(tool.getToolName());
            pendingAction.setTitle(plan.getTitle());
            pendingAction.setSummaryItems(plan.getSummaryItems());
            pendingAction.setConfirmRequired(Boolean.TRUE);

            AiAssistantTurnVO turn = baseTurn(session.getSessionId(), "confirm", "awaiting_confirmation", plan.getAssistantText(), plan.getSuggestions(),
                    List.of(AiAssistantCardVO.confirmation(pendingAction, plan.getAssistantText())));
            turn.setPendingAction(pendingAction);
            return turn;
        }

        AiAssistantTurnVO turn = baseTurn(session.getSessionId(), plan.getStage(), resolveState(plan.getStage(), tool), plan.getAssistantText(), plan.getSuggestions(),
                cardsForPlan(tool, plan));
        turn.setMissingFields(plan.getMissingFields());
        turn.setResult(plan.getResult());
        sessionStore.rememberMessage(session, "assistant", turn.getMessage());
        return turn;
    }

    private AiAssistantTurnVO resultTurn(String sessionId, AiAssistantExecutionResult executionResult) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus(executionResult.getStatus());
        result.setTitle(executionResult.getTitle());
        result.setSummaryItems(executionResult.getSummaryItems());
        result.setDeepLink(executionResult.getDeepLink());

        AiAssistantTurnVO turn = baseTurn(sessionId, "result", "executed", executionResult.getAssistantText(), DEFAULT_SUGGESTIONS, List.of());
        turn.setResult(result);
        turn.setCards(List.of(AiAssistantCardVO.executionResult(result, executionResult.getAssistantText())));
        return turn;
    }

    private AiAssistantTurnVO invalidExecutionTurn() {
        return errorTurn("asst-invalid", INVALID_EXECUTION_MESSAGE);
    }

    private AiAssistantTurnVO errorTurn(String sessionId, String assistantText) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle("动作执行失败");
        return errorTurn(sessionId, assistantText, result);
    }

    private AiAssistantTurnVO errorTurn(String sessionId, String assistantText, AiAssistantResultVO result) {
        AiAssistantTurnVO turn = baseTurn(sessionId, "error", "error", assistantText, DEFAULT_SUGGESTIONS, List.of(AiAssistantCardVO.error(assistantText, result)));
        turn.setResult(result);
        return turn;
    }

    private AiAssistantTurnVO textTurn(String sessionId, String message) {
        return baseTurn(sessionId, "reply", "idle", message, DEFAULT_SUGGESTIONS, List.of(AiAssistantCardVO.text(message)));
    }

    private AiAssistantTurnVO ragTurn(String sessionId, AiAssistantRagService.Answer answer) {
        String message = answer == null ? OUT_OF_SCOPE_MESSAGE : answer.message();
        List<String> suggestions = answer == null ? DEFAULT_SUGGESTIONS : answer.suggestions();
        return baseTurn(sessionId, "reply", "idle", message, suggestions, List.of(AiAssistantCardVO.text("系统帮助", message)));
    }

    private AiAssistantTurnVO clarificationTurn(String sessionId, String message, List<String> suggestions) {
        String text = message == null || message.isBlank() ? "请再说具体一点。" : message;
        return baseTurn(sessionId, "collect", "collecting", text, suggestions, List.of(AiAssistantCardVO.clarification(text)));
    }

    private AiAssistantTurnVO permissionDeniedTurn(String sessionId, AiAssistantToolDefinition tool) {
        String message = "该操作仅管理员可用。";
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle("无权限");
        return baseTurn(sessionId, "error", "error", message, DEFAULT_SUGGESTIONS, List.of(AiAssistantCardVO.error(message, result)));
    }

    private List<AiAssistantCardVO> cardsForPlan(AiAssistantToolDefinition tool, AiAssistantActionPlan plan) {
        if ("collect".equals(plan.getStage())) {
            if (plan.getMissingFields().isEmpty()) {
                return List.of(AiAssistantCardVO.clarification(plan.getAssistantText()));
            }
            return List.of(AiAssistantCardVO.fieldForm(plan.getAssistantText(), plan.getMissingFields()));
        }
        if ("error".equals(plan.getStage())) {
            return List.of(AiAssistantCardVO.error(plan.getAssistantText(), plan.getResult()));
        }
        if ("reply".equals(plan.getStage())) {
            if ("read".equals(tool.getOperationType())) {
                return List.of(AiAssistantCardVO.queryResult(tool.getDescription(), plan.getAssistantText()));
            }
            return List.of(AiAssistantCardVO.text(plan.getAssistantText()));
        }
        return List.of(AiAssistantCardVO.text(plan.getAssistantText()));
    }

    private String resolveState(String stage, AiAssistantToolDefinition tool) {
        if ("collect".equals(stage)) {
            return "collecting";
        }
        if ("error".equals(stage)) {
            return "error";
        }
        if ("reply".equals(stage) && "read".equals(tool.getOperationType())) {
            return "executed";
        }
        return "idle";
    }

    private AiAssistantTurnVO baseTurn(String sessionId,
                                       String stage,
                                       String state,
                                       String assistantText,
                                       List<String> suggestions,
                                       List<AiAssistantCardVO> cards) {
        AiAssistantTurnVO turn = new AiAssistantTurnVO();
        turn.setSessionId(sessionId);
        turn.setTurnId("turn-" + UUID.randomUUID().toString().replace("-", ""));
        turn.setRole("assistant");
        turn.setMessage(assistantText);
        turn.setState(state);
        turn.setCards(cards == null ? List.of() : cards);
        turn.setStage(stage);
        turn.setAssistantText(assistantText);
        turn.setSuggestions(suggestions == null ? List.of() : suggestions);
        return turn;
    }

    private boolean shouldClarifyUnknown(AiAssistantIntentParseResult parseResult) {
        String text = parseResult == null ? null : parseResult.getNormalizedText();
        return containsAny(text, "预约", "会议", "会议室", "安排", "日程", "排期", "明天的", "那个会", "这场会", "处理一下");
    }

    private boolean isCancelRoomAmbiguity(String normalizedText) {
        return containsAny(normalizedText, "取消这个会议室", "取消这间会议室", "取消会议室");
    }

    private boolean containsAny(String text, String... fragments) {
        if (text == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private Long requireUserId(AuthUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BizException(401, "not logged in");
        }
        return currentUser.getId();
    }

    private void audit(Long userId, AiAssistantSessionStore.PendingExecution execution, String result, String resultTitle) {
        log.info("ai_assistant_audit userId={} sessionId={} executionId={} actionType={} params={} result={} createdAt={} title={}",
                userId,
                execution.getSessionId(),
                execution.getExecutionId(),
                execution.getActionType(),
                execution.getParams(),
                result,
                LocalDateTime.now(),
                resultTitle);
    }
}
