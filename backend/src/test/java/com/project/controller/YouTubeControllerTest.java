package com.project.controller;

import com.project.service.YouTubeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

public class YouTubeControllerTest {

    private YouTubeService youtubeService;
    private YouTubeController youtubeController;

    @BeforeEach
    void setUp() {
        youtubeService = Mockito.mock(YouTubeService.class);
        youtubeController = new YouTubeController(youtubeService);
    }

    @Test
    void testSearchYouTube() {
        Map<String, Object> result = Map.of("items", List.of(Map.of("id", Map.of("videoId", "test12345"))));
        ResponseEntity resp = ResponseEntity.ok(result);
        when(youtubeService.searchYouTube("lofi")).thenReturn(resp);

        ResponseEntity<?> response = youtubeController.searchYouTube("lofi");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(result, response.getBody());
    }
}
