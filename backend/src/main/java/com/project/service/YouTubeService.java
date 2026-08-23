package com.project.service;

import org.springframework.http.ResponseEntity;

public interface YouTubeService {
    ResponseEntity<?> searchYouTube(String query);
}
