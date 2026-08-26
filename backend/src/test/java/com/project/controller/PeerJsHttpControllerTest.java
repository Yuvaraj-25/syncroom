package com.project.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PeerJsHttpControllerTest {

    private PeerJsHttpController controller;

    @BeforeEach
    void setUp() {
        controller = new PeerJsHttpController();
    }

    @Test
    void testGetPeerId() {
        ResponseEntity<String> response = controller.getPeerId();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isBlank());
    }

    @Test
    void testGetPeers() {
        ResponseEntity<List<String>> response = controller.getPeers();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
