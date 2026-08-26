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
            log.info("OpenRouter API key not configured, using smart offline response for query: {}", userQuery);
            String smartResponse = getSmartFallbackResponse(userQuery);
            Map<String, Object> fallbackMsg = new HashMap<>();
            fallbackMsg.put("id", randomId);
            fallbackMsg.put("userId", "ai-assistant");
            fallbackMsg.put("userName", "SyncRoom AI 🤖");
            fallbackMsg.put("userImage", "https://api.dicebear.com/7.x/bottts/svg?seed=syncroomai");
            fallbackMsg.put("text", smartResponse);
            fallbackMsg.put("timestamp", timestamp);
            fallbackMsg.put("isAi", true);
            return fallbackMsg;
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
            errorMsg.put("text", "Sorry, I'm having trouble connecting right now. Please check my API key configuration.");
            errorMsg.put("timestamp", timestamp);
            errorMsg.put("isAi", true);
            return errorMsg;
        }
    }

    private String getSmartFallbackResponse(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "👋 Hey there! Welcome to **SyncRoom**! I'm your AI watch-party assistant. How can I help you and your room members today?";
        }
        if (lower.contains("recommend") || lower.contains("movie") || lower.contains("video") || lower.contains("watch")) {
            return "🎬 **Top Watch-Party Suggestions:**\n" +
                    "1. *Lofi Hip Hop Radio - Beats to Relax/Study to*\n" +
                    "2. *Kurzgesagt – In a Nutshell science explorations*\n" +
                    "3. *Veritasium or Mark Rober tech adventures*\n" +
                    "4. *Free Blender Foundation Animated Open Movies (Big Buck Bunny, Sintel)*\n\n" +
                    "You can paste any YouTube URL or search directly from the **Queue panel**!";
        }
        if (lower.contains("help") || lower.contains("how to") || lower.contains("feature")) {
            return "💡 **SyncRoom Quick Tips:**\n" +
                    "- **Video Sync:** Room owners control playback, pause, and seek for everyone.\n" +
                    "- **Whiteboard:** Click the Whiteboard button to draw collaboratively in real-time.\n" +
                    "- **Polls & Q&A:** Use the Polls sidebar to host interactive votes or take questions.\n" +
                    "- **Voice & Video:** Toggle your mic and camera in the bottom control bar!";
        }
        return "🤖 You asked: *\"" + query + "\"*\n\n" +
                "I'm currently running in standard assistant mode. (To enable deep live AI reasoning, set the `OPENROUTER_API_KEY` environment variable in your backend). Enjoy watching together in **SyncRoom**!";
    }
}
