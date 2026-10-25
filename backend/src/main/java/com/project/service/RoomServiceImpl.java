package com.project.service;

import com.project.dto.RoomCreateRequest;
import com.project.dto.RoomResponse;
import com.project.exception.BadRequestException;
import com.project.exception.ResourceNotFoundException;
import com.project.model.Room;
import com.project.repository.MessageRepository;
import com.project.repository.PinnedNoteRepository;
import com.project.repository.RoomRepository;
import com.project.repository.RoomMediaStateRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageRepository messageRepository;
    private final PinnedNoteRepository pinnedNoteRepository;
    private final RoomMediaStateRepository roomMediaStateRepository;

    public RoomServiceImpl(RoomRepository roomRepository, PasswordEncoder passwordEncoder,
                           MessageRepository messageRepository, PinnedNoteRepository pinnedNoteRepository,
                           RoomMediaStateRepository roomMediaStateRepository) {
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
        this.messageRepository = messageRepository;
        this.pinnedNoteRepository = pinnedNoteRepository;
        this.roomMediaStateRepository = roomMediaStateRepository;
    }

    @Override
    public RoomResponse getRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new BadRequestException("Room ID is required");
        }

        Room room = roomRepository.findByRoomId(roomId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (room.isClosed()) {
            throw new ResourceNotFoundException("Room not found");
        }

        return toResponse(room);
    }

    @Override
    @Transactional
    public synchronized boolean prepareRoomForJoin(String roomId, String ownerId) {
        if (roomId == null || roomId.isBlank() || ownerId == null || ownerId.isBlank()) {
            return false;
        }

        String normalizedRoomId = roomId.trim();
        return roomRepository.findByRoomId(normalizedRoomId)
                .map(room -> !room.isClosed())
                .orElseGet(() -> {
                    Room room = new Room(normalizedRoomId, ownerId.trim(), false, null);
                    Instant now = Instant.now();
                    room.setCreatedAt(now);
                    room.setUpdatedAt(now);
                    roomRepository.save(room);
                    return true;
                });
    }

    @Override
    public boolean isRoomCreator(String roomId, String userId) {
        if (roomId == null || roomId.isBlank() || userId == null || userId.isBlank()) {
            return false;
        }

        return roomRepository.findByRoomId(roomId.trim())
                .filter(room -> !room.isClosed())
                .map(room -> userId.trim().equals(room.getOwnerId()))
                .orElse(false);
    }

    @Override
    @Transactional
    public synchronized boolean closeRoom(String roomId, String ownerId) {
        if (roomId == null || roomId.isBlank() || ownerId == null || ownerId.isBlank()) {
            return false;
        }

        Optional<Room> roomOptional = roomRepository.findByRoomId(roomId.trim());
        if (roomOptional.isEmpty()) {
            return false;
        }

        Room room = roomOptional.get();
        if (room.isClosed() || !ownerId.trim().equals(room.getOwnerId())) {
            return false;
        }

        room.setClosed(true);
        roomRepository.save(room);
        messageRepository.deleteByRoomId(room.getRoomId());
        pinnedNoteRepository.deleteByRoomId(room.getRoomId());
        roomMediaStateRepository.deleteById(room.getRoomId());
        return true;
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
        room.setCreatedAt(Instant.now());
        room.setUpdatedAt(Instant.now());

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
