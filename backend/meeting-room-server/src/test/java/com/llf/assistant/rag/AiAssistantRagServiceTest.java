package com.llf.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantRagServiceTest {

    @Test
    void answer_cancelRule_shouldReturnKnowledgeAnswer() {
        AiAssistantRagService.Answer answer = ragService().answer("怎么取消预约");

        assertTrue(answer.matched());
        assertTrue(answer.message().contains("取消"));
        assertTrue(answer.message().contains("预约"));
        assertFalse(answer.suggestions().isEmpty());
    }

    @Test
    void answer_adminRejectRule_shouldReturnApprovalKnowledge() {
        AiAssistantRagService.Answer answer = ragService().answer("审批驳回需要填原因吗");

        assertTrue(answer.matched());
        assertTrue(answer.message().contains("驳回"));
        assertTrue(answer.message().contains("原因"));
    }

    @Test
    void answer_weather_shouldReturnOutOfScope() {
        AiAssistantRagService.Answer answer = ragService().answer("今天天气怎么样");

        assertFalse(answer.matched());
        assertTrue(answer.message().contains("系统") || answer.message().contains("会议"));
    }

    private AiAssistantRagService ragService() {
        return new AiAssistantRagService(new AiAssistantKnowledgeService());
    }
}
