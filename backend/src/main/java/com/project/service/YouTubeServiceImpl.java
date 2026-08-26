package com.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YouTubeServiceImpl implements YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeServiceImpl.class);

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public YouTubeServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public ResponseEntity<?> searchYouTube(String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.nested("Query parameter q is required"));
        }

        // 1. If official API key is provided, use YouTube Data API v3
        if (youtubeApiKey != null && !youtubeApiKey.isBlank()) {
            try {
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = String.format(
                        "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=5&q=%s&type=video&key=%s",
                        encodedQuery,
                        youtubeApiKey
                );

                ResponseEntity<String> response = restClient.get()
                        .uri(url)
                        .retrieve()
                        .toEntity(String.class);

                Object jsonBody = objectMapper.readValue(response.getBody(), Object.class);
                return ResponseEntity.status(response.getStatusCode()).body(jsonBody);
            } catch (Exception ex) {
                log.warn("Official YouTube API failed, falling back to public search: {}", ex.getMessage());
            }
        }

        // 2. Fallback: Parse YouTube public search results (works without API key)
        try {
            List<Map<String, Object>> items = fallbackSearch(query);
            if (!items.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("items", items);
                return ResponseEntity.ok(result);
            }
        } catch (Exception ex) {
            log.error("Fallback YouTube search error: ", ex);
        }

        // 3. If fallback returned nothing and no key was configured, report NOT_FOUND so client knows
        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.nested("YouTube API Key is not configured on the server and fallback yielded no results"));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.nested("Failed to search YouTube videos"));
    }

    private List<Map<String, Object>> fallbackSearch(String query) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://www.youtube.com/results?search_query=" + encodedQuery;

            String html = restClient.get()
                    .uri(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .retrieve()
                    .body(String.class);

            if (html == null) return items;

            // Pattern for videoId and title in initial data JSON
            Pattern videoPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]");
            Matcher matcher = videoPattern.matcher(html);

            while (matcher.find() && items.size() < 6) {
                String videoId = matcher.group(1);
                String rawTitle = matcher.group(2);
                String title = unescapeUnicode(rawTitle);

                if (!seenIds.add(videoId)) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("id", Map.of("videoId", videoId));

                Map<String, Object> snippet = new HashMap<>();
                snippet.put("title", title);
                snippet.put("channelTitle", "YouTube Creator");
                snippet.put("thumbnails", Map.of(
                        "medium", Map.of("url", "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg"),
                        "default", Map.of("url", "https://i.ytimg.com/vi/" + videoId + "/default.jpg")
                ));

                item.put("snippet", snippet);
                items.add(item);
            }
        } catch (Exception e) {
            log.debug("Fallback scraper error for query '{}': {}", query, e.getMessage());
        }

        return items;
    }

    private String unescapeUnicode(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        int len = input.length();
        int i = 0;
        while (i < len) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < len && input.charAt(i + 1) == 'u' && i + 5 < len) {
                try {
                    String hex = input.substring(i + 2, i + 6);
                    int code = Integer.parseInt(hex, 16);
                    sb.append((char) code);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(c);
            i++;
        }
        return sb.toString().replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

