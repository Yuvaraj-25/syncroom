package com.project.controller;

import com.project.dto.RoomCreateRequest;
import com.project.dto.RoomResponse;
import com.project.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RoomControllerTest {

    private RoomService roomService;
    private RoomController roomController;

    @BeforeEach
    public void setup() {
        roomService = Mockito.mock(RoomService.class);
        roomController = new RoomController(roomService);
    }

    @Test
    public void testGetRoom() {
        RoomResponse roomResponse = new RoomResponse("id1", "ROOM1", "owner1", false, Instant.now(), Instant.now());
        when(roomService.getRoom("ROOM1")).thenReturn(roomResponse);

        ResponseEntity<RoomResponse> response = roomController.getRoom("ROOM1");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ROOM1", response.getBody().getRoomId());
    }

    @Test
    public void testCreateRoom() {
        RoomResponse roomResponse = new RoomResponse("id1", "ROOM1", "owner1", false, Instant.now(), Instant.now());
        when(roomService.createRoom(any(RoomCreateRequest.class))).thenReturn(roomResponse);

        RoomCreateRequest request = new RoomCreateRequest("ROOM1", "owner1", false, null);
        ResponseEntity<RoomResponse> response = roomController.createRoom(request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("ROOM1", response.getBody().getRoomId());
    }
}
