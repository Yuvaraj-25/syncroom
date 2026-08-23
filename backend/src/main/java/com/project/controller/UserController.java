package com.project.controller;

import com.project.dto.*;
import com.project.model.User;
import com.project.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sync")
    public ResponseEntity<User> syncUser(@Valid @RequestBody UserSyncRequest request) {
        User user = userService.syncUser(request);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@RequestParam(required = false) String email) {
        UserProfileResponse profile = userService.getProfile(email);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/profile/update")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        ProfileUpdateResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent-rooms")
    public ResponseEntity<List<RecentRoomDto>> getRecentRooms(@RequestParam(required = false) String email) {
        List<RecentRoomDto> recentRooms = userService.getRecentRooms(email);
        return ResponseEntity.ok(recentRooms);
    }

    @PostMapping("/recent-rooms")
    public ResponseEntity<List<RecentRoomDto>> addRecentRoom(@Valid @RequestBody RecentRoomRequest request) {
        List<RecentRoomDto> recentRooms = userService.addRecentRoom(request);
        return ResponseEntity.ok(recentRooms);
    }
}
