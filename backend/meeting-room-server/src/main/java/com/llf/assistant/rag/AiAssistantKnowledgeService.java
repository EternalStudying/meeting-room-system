package com.llf.assistant.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class AiAssistantKnowledgeService {

    private static final String KNOWLEDGE_RESOURCE = "ai/assistant-knowledge.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AiAssistantKnowledgeItem> listKnowledge() {
        try (InputStream inputStream = new ClassPathResource(KNOWLEDGE_RESOURCE).getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("failed to load assistant knowledge", e);
        }
    }
}
