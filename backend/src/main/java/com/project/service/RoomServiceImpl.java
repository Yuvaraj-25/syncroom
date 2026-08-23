package com.project.service;

import com.project.dto.RoomCreateRequest;
import com.project.dto.RoomResponse;
import com.project.exception.BadRequestException;
import com.project.exception.ResourceNotFoundException;
import com.project.model.Room;
import com.project.repository.RoomRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    public RoomServiceImpl(RoomRepository roomRepository, PasswordEncoder passwordEncoder) {
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public RoomResponse getRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new BadRequestException("Room ID is required");
        }

        Room room = roomRepository.findByRoomId(roomId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        return toResponse(room);
    }

    @Override
    public RoomResponse createRoom(RoomCreateRequest request) {
        if (request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new BadRequestException("Room ID is required");
        }
        if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
            throw new BadRequestException("Owner ID is required");
        }

        String roomId = request.getRoomId().trim();
        if (roomRepository.existsByRoomId(roomId)) {
            throw new BadRequestException("Room ID already exists");
        }

        String password = request.getPassword();
        if (request.isPrivate() && password != null && !password.isBlank()) {
            password = passwordEncoder.encode(password);
        }

        Room room = new Room(roomId, request.getOwnerId().trim(), request.isPrivate(), password);
        room.setCreatedAt(new Date());
        room.setUpdatedAt(new Date());

        Room savedRoom = roomRepository.save(room);
        return toResponse(savedRoom);
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomId(),
                room.getOwnerId(),
                room.isPrivate(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
