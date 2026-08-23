package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiAssistantService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public boolean isAiTrigger(String text) {
        if (text == null) return false;
        String trimmed = text.trim().toLowerCase();
        return trimmed.startsWith("@ai ") || trimmed.equals("@ai");
    }

    public Map<String, Object> generateAiResponse(String messageText) {
        String userQuery = messageText.replaceAll("(?i)^@ai", "").trim();
        if (userQuery.isEmpty()) {
            userQuery = "Hello!";
        }

        String randomId = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        String timestamp = Instant.now().toString();

        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            log.warn("OpenRouter API key is missing");
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("id", randomId);
            errorMsg.put("userId", "ai-assistant");
            errorMsg.put("userName", "SyncRoom AI 🤖");
            errorMsg.put("userImage", "https://api.dicebear.com/7.x/bottts/svg?seed=syncroomai");
            errorMsg.put("text", "Sorry, I'm having trouble connecting right now. Please make sure my API key is configured correctly.");
            errorMsg.put("timestamp", timestamp);
            errorMsg.put("isAi", true);
            return errorMsg;
        }

        try {
            Map<String, Object> systemMsg = Map.of(
                    "role", "system",
                    "content", "You are an AI assistant in a real-time collaborative room called SyncRoom. You help users with their questions. Keep answers concise, helpful, and friendly. You can use simple markdown if necessary."
            );
            Map<String, Object> userMsg = Map.of(
                    "role", "user",
                    "content", userQuery
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "google/gemini-pro",
                    "messages", List.of(systemMsg, userMsg)
            );

            String responseBody = restClient.post()
                    .uri("https://openrouter.ai/api/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String aiText = root.path("choices").path(0).path("message").path("content").asText("Sorry, I couldn't generate a response.");

            Map<String, Object> successMsg = new HashMap<>();
            successMsg.put("id", randomId);
            successMsg.put("userId", "ai-assistant");
            successMsg.put("userName", "SyncRoom AI 🤖");
            successMsg.put("userImage", "https://api.dicebear.com/7.x/bottts/svg?seed=syncroomai");
            successMsg.put("text", aiText);
            successMsg.put("timestamp", timestamp);
            successMsg.put("isAi", true);
            return successMsg;
        } catch (Exception e) {
            log.error("AI Error generating response: ", e);
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("id", randomId);
            errorMsg.put("userId", "ai-assistant");
            errorMsg.put("userName", "SyncRoom AI 🤖");
            errorMsg.put("userImage", "https://api.dicebear.com/7.x/bottts/svg?seed=syncroomai");
            errorMsg.put("text", "Sorry, I'm having trouble connecting right now. Please make sure my API key is configured correctly.");
            errorMsg.put("timestamp", timestamp);
            errorMsg.put("isAi", true);
            return errorMsg;
        }
    }
}
