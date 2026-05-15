package com.llf.assistant.rag;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AiAssistantRagService {

    private final AiAssistantKnowledgeService knowledgeService;

    public AiAssistantRagService(AiAssistantKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public Answer answer(String userText) {
        String source = userText == null ? "" : userText.trim();
        List<ScoredItem> scoredItems = knowledgeService.listKnowledge().stream()
                .filter(item -> !"out_of_scope".equals(item.getId()))
                .map(item -> new ScoredItem(item, score(source, item)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed())
                .toList();
        if (scoredItems.isEmpty()) {
            AiAssistantKnowledgeItem outOfScope = knowledgeService.listKnowledge().stream()
                    .filter(item -> "out_of_scope".equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            String message = outOfScope == null
                    ? "我只能回答会议室预约系统相关规则和操作指引。"
                    : outOfScope.getContent();
            List<String> suggestions = outOfScope == null ? List.of("怎么取消预约", "查看我的预约") : outOfScope.getSuggestions();
            return new Answer(false, "能力范围", message, suggestions);
        }
        AiAssistantKnowledgeItem item = scoredItems.get(0).item();
        return new Answer(true, item.getTitle(), item.getContent(), item.getSuggestions());
    }

    private int score(String source, AiAssistantKnowledgeItem item) {
        int score = 0;
        if (source.contains(item.getTitle())) {
            score += 3;
        }
        for (String keyword : item.getKeywords()) {
            if (keyword != null && !keyword.isBlank() && source.contains(keyword)) {
                score += 2;
            }
        }
        if (item.getContent() != null) {
            for (String token : source.split("[，。,.\\s]+")) {
                if (!token.isBlank() && token.length() >= 2 && item.getContent().contains(token)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private record ScoredItem(AiAssistantKnowledgeItem item, int score) {
    }

    public record Answer(boolean matched, String title, String message, List<String> suggestions) {
    }
}
