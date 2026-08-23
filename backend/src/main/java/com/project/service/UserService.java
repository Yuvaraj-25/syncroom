package com.project.service;

import com.project.dto.*;
import com.project.model.User;

import java.util.List;

public interface UserService {
    User syncUser(UserSyncRequest request);
    UserProfileResponse getProfile(String email);
    ProfileUpdateResponse updateProfile(UserProfileUpdateRequest request);
    List<RecentRoomDto> getRecentRooms(String email);
    List<RecentRoomDto> addRecentRoom(RecentRoomRequest request);
}
