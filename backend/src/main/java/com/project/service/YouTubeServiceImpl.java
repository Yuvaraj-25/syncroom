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

        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.nested("YouTube API Key is not configured on the server"));
        }

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
            log.error("YouTube search error: ", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.nested("Failed to search YouTube videos"));
        }
    }
}
