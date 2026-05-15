package com.llf.assistant.rag;

import lombok.Data;

import java.util.List;

@Data
public class AiAssistantKnowledgeItem {
    private String id;
    private String title;
    private String category;
    private String content;
    private List<String> keywords = List.of();
    private List<String> suggestions = List.of();
}
