package com.project.service;

import com.project.dto.*;
import com.project.exception.BadRequestException;
import com.project.exception.ResourceNotFoundException;
import com.project.model.RecentRoom;
import com.project.model.User;
import com.project.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private String getDefaultDiceBearAvatar(String email) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "https://api.dicebear.com/7.x/adventurer/svg?seed=" + encodedEmail;
    }

    @Override
    public User syncUser(UserSyncRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Email is required");
        }

        String email = request.getEmail().trim();
        log.debug("Syncing user with email: {}", email);
        Optional<User> optionalUser = userRepository.findByEmail(email);

        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            // Do NOT overwrite user's manual edits if they already exist in database
            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(request.getName());
            }
            if (user.getImage() == null || user.getImage().isBlank()) {
                user.setImage(request.getImage() != null && !request.getImage().isBlank()
                        ? request.getImage()
                        : getDefaultDiceBearAvatar(email));
            }
            user.setUpdatedAt(new Date());
            user = userRepository.save(user);
        } else {
            String image = request.getImage() != null && !request.getImage().isBlank()
                    ? request.getImage()
                    : getDefaultDiceBearAvatar(email);
            user = new User(request.getName(), email, image);
            user.setCreatedAt(new Date());
            user.setUpdatedAt(new Date());
            user = userRepository.save(user);
        }

        return user;
    }

    @Override
    public UserProfileResponse getProfile(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return new UserProfileResponse(user.getName(), user.getEmail(), user.getImage());
    }

    @Override
    public ProfileUpdateResponse updateProfile(UserProfileUpdateRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("Email is required");
        }

        User user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getImage() != null) {
            user.setImage(request.getImage());
        }
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        return new ProfileUpdateResponse(
                "Profile updated successfully",
                new UserProfileResponse(user.getName(), user.getEmail(), user.getImage())
        );
    }

    @Override
    public List<RecentRoomDto> getRecentRooms(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<RecentRoom> rooms = user.getRecentRooms();
        if (rooms == null) {
            return Collections.emptyList();
        }

        return rooms.stream()
                .sorted((a, b) -> {
                    Date dateA = a.getJoinedAt() != null ? a.getJoinedAt() : new Date(0);
                    Date dateB = b.getJoinedAt() != null ? b.getJoinedAt() : new Date(0);
                    return dateB.compareTo(dateA);
                })
                .map(r -> new RecentRoomDto(r.getRoomId(), r.getJoinedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<RecentRoomDto> addRecentRoom(RecentRoomRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new BadRequestException("Email and roomId are required");
        }

        String email = request.getEmail().trim();
        String roomId = request.getRoomId().trim();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found. Please sync user first."));

        List<RecentRoom> recentRooms = user.getRecentRooms();
        // Remove room if it already exists (to move it to top)
        recentRooms.removeIf(r -> roomId.equalsIgnoreCase(r.getRoomId()));

        // Add room to the beginning of the array
        recentRooms.add(0, new RecentRoom(roomId, new Date()));

        // Keep only 10 most recent rooms
        if (recentRooms.size() > 10) {
            recentRooms = new ArrayList<>(recentRooms.subList(0, 10));
            user.setRecentRooms(recentRooms);
        }

        user.setUpdatedAt(new Date());
        userRepository.save(user);

        return recentRooms.stream()
                .map(r -> new RecentRoomDto(r.getRoomId(), r.getJoinedAt()))
                .collect(Collectors.toList());
    }
}
