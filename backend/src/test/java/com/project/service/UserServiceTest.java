package com.project.service;

import com.project.dto.*;
import com.project.exception.BadRequestException;
import com.project.exception.ResourceNotFoundException;
import com.project.model.RecentRoom;
import com.project.model.User;
import com.project.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    private UserRepository userRepository;
    private UserServiceImpl userService;

    @BeforeEach
    public void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    public void testSyncUser_NewUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSyncRequest request = new UserSyncRequest("Test User", "test@example.com", "http://example.com/img.png");
        User user = userService.syncUser(request);

        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", user.getName());
        assertEquals("http://example.com/img.png", user.getImage());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    public void testSyncUser_ExistingUserPreserveName() {
        User existing = new User("Custom Name", "test@example.com", "http://custom.com/img.png");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserSyncRequest request = new UserSyncRequest("OAuth Name", "test@example.com", "http://oauth.com/img.png");
        User user = userService.syncUser(request);

        assertNotNull(user);
        assertEquals("Custom Name", user.getName());
        assertEquals("http://custom.com/img.png", user.getImage());
    }

    @Test
    public void testGetProfile_Success() {
        User user = new User("Test User", "test@example.com", "http://example.com/img.png");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile("test@example.com");
        assertNotNull(response);
        assertEquals("Test User", response.getName());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    public void testGetProfile_NotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile("nonexistent@example.com"));
    }

    @Test
    public void testUpdateProfile_Success() {
        User user = new User("Old Name", "test@example.com", "http://old.png");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileUpdateRequest request = new UserProfileUpdateRequest("test@example.com", "New Name", "http://new.png");
        ProfileUpdateResponse response = userService.updateProfile(request);

        assertNotNull(response);
        assertEquals("Profile updated successfully", response.getMessage());
        assertEquals("New Name", response.getUser().getName());
        assertEquals("http://new.png", response.getUser().getImage());
    }

    @Test
    public void testRecentRooms_AddAndLimit() {
        User user = new User("Test", "test@example.com", "img");
        List<RecentRoom> rooms = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rooms.add(new RecentRoom("ROOM" + i, Instant.now().minusSeconds(i)));
        }
        user.setRecentRooms(rooms);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        RecentRoomRequest request = new RecentRoomRequest("test@example.com", "NEWROOM");
        List<RecentRoomDto> result = userService.addRecentRoom(request);

        assertEquals(10, result.size());
        assertEquals("NEWROOM", result.get(0).getRoomId());
    }

    @Test
    public void testSyncUser_MissingEmail() {
        UserSyncRequest request = new UserSyncRequest("Test User", null, null);
        assertThrows(BadRequestException.class, () -> userService.syncUser(request));
    }

    @Test
    public void testGetProfile_MissingEmail() {
        assertThrows(BadRequestException.class, () -> userService.getProfile(null));
        assertThrows(BadRequestException.class, () -> userService.getProfile("   "));
    }

    @Test
    public void testUpdateProfile_MissingEmail() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(null, "Name", null);
        assertThrows(BadRequestException.class, () -> userService.updateProfile(request));
    }

    @Test
    public void testGetRecentRooms_MissingEmail() {
        assertThrows(BadRequestException.class, () -> userService.getRecentRooms(null));
    }

    @Test
    public void testAddRecentRoom_MissingParams() {
        assertThrows(BadRequestException.class, () -> userService.addRecentRoom(new RecentRoomRequest(null, "ROOM")));
        assertThrows(BadRequestException.class, () -> userService.addRecentRoom(new RecentRoomRequest("email@test.com", null)));
    }
}
