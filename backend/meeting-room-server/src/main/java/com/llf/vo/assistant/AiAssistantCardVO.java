package com.llf.vo.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantCardVO {
    private String type;
    private String title;
    private String message;
    private List<AiAssistantSummaryItemVO> summaryItems = new ArrayList<>();
    private List<AiAssistantMissingFieldVO> fields = new ArrayList<>();
    private AiAssistantPendingActionVO pendingAction;
    private AiAssistantResultVO result;

    public static AiAssistantCardVO text(String message) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("text");
        card.setMessage(message);
        return card;
    }

    public static AiAssistantCardVO text(String title, String message) {
        AiAssistantCardVO card = text(message);
        card.setTitle(title);
        return card;
    }

    public static AiAssistantCardVO queryResult(String title, String message) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("query_result");
        card.setTitle(title);
        card.setMessage(message);
        return card;
    }

    public static AiAssistantCardVO fieldForm(String message, List<AiAssistantMissingFieldVO> fields) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("field_form");
        card.setTitle("补齐参数");
        card.setMessage(message);
        card.setFields(fields == null ? List.of() : fields);
        return card;
    }

    public static AiAssistantCardVO confirmation(AiAssistantPendingActionVO pendingAction, String message) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("confirmation");
        card.setTitle(pendingAction == null ? "确认操作" : pendingAction.getTitle());
        card.setMessage(message);
        card.setPendingAction(pendingAction);
        card.setSummaryItems(pendingAction == null ? List.of() : pendingAction.getSummaryItems());
        return card;
    }

    public static AiAssistantCardVO executionResult(AiAssistantResultVO result, String message) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("execution_result");
        card.setTitle(result == null ? "执行结果" : result.getTitle());
        card.setMessage(message);
        card.setResult(result);
        card.setSummaryItems(result == null ? List.of() : result.getSummaryItems());
        return card;
    }

    public static AiAssistantCardVO clarification(String message) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("clarification");
        card.setTitle("需要补充信息");
        card.setMessage(message);
        return card;
    }

    public static AiAssistantCardVO error(String message, AiAssistantResultVO result) {
        AiAssistantCardVO card = new AiAssistantCardVO();
        card.setType("error");
        card.setTitle(result == null ? "处理失败" : result.getTitle());
        card.setMessage(message);
        card.setResult(result);
        return card;
    }
}
