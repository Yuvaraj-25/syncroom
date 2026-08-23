package com.project.controller;

import com.project.dto.*;
import com.project.model.User;
import com.project.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class UserControllerTest {

    private UserService userService;
    private UserController userController;

    @BeforeEach
    public void setup() {
        userService = Mockito.mock(UserService.class);
        userController = new UserController(userService);
    }

    @Test
    public void testSyncUser() {
        User user = new User("Test User", "test@example.com", "http://image.png");
        when(userService.syncUser(any(UserSyncRequest.class))).thenReturn(user);

        UserSyncRequest request = new UserSyncRequest("Test User", "test@example.com", "http://image.png");
        ResponseEntity<User> response = userController.syncUser(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("test@example.com", response.getBody().getEmail());
    }

    @Test
    public void testGetProfile() {
        UserProfileResponse profile = new UserProfileResponse("Test User", "test@example.com", "http://image.png");
        when(userService.getProfile("test@example.com")).thenReturn(profile);

        ResponseEntity<UserProfileResponse> response = userController.getProfile("test@example.com");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Test User", response.getBody().getName());
    }

    @Test
    public void testUpdateProfile() {
        ProfileUpdateResponse updateResponse = new ProfileUpdateResponse("Profile updated successfully",
                new UserProfileResponse("New Name", "test@example.com", "http://image.png"));
        when(userService.updateProfile(any(UserProfileUpdateRequest.class))).thenReturn(updateResponse);

        UserProfileUpdateRequest request = new UserProfileUpdateRequest("test@example.com", "New Name", "http://image.png");
        ResponseEntity<ProfileUpdateResponse> response = userController.updateProfile(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("New Name", response.getBody().getUser().getName());
    }

    @Test
    public void testGetRecentRooms() {
        List<RecentRoomDto> rooms = List.of(new RecentRoomDto("ROOM1", new Date()));
        when(userService.getRecentRooms("test@example.com")).thenReturn(rooms);

        ResponseEntity<List<RecentRoomDto>> response = userController.getRecentRooms("test@example.com");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testAddRecentRoom() {
        List<RecentRoomDto> rooms = List.of(new RecentRoomDto("ROOM1", new Date()));
        when(userService.addRecentRoom(any(RecentRoomRequest.class))).thenReturn(rooms);

        RecentRoomRequest request = new RecentRoomRequest("test@example.com", "ROOM1");
        ResponseEntity<List<RecentRoomDto>> response = userController.addRecentRoom(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }
}
