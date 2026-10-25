package com.project.service;

import com.project.dto.RoomCreateRequest;
import com.project.dto.RoomResponse;
import com.project.exception.BadRequestException;
import com.project.exception.ResourceNotFoundException;
import com.project.model.Room;
import com.project.repository.RoomRepository;
import com.project.repository.MessageRepository;
import com.project.repository.PinnedNoteRepository;
import com.project.repository.RoomMediaStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RoomServiceTest {

    private RoomRepository roomRepository;
    private PasswordEncoder passwordEncoder;
    private MessageRepository messageRepository;
    private PinnedNoteRepository pinnedNoteRepository;
    private RoomMediaStateRepository roomMediaStateRepository;
    private RoomServiceImpl roomService;

    @BeforeEach
    public void setup() {
        roomRepository = Mockito.mock(RoomRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        pinnedNoteRepository = Mockito.mock(PinnedNoteRepository.class);
        roomMediaStateRepository = Mockito.mock(RoomMediaStateRepository.class);
        roomService = new RoomServiceImpl(roomRepository, passwordEncoder, messageRepository,
                pinnedNoteRepository, roomMediaStateRepository);
    }

    @Test
    public void testGetRoom_Success() {
        Room room = new Room("ABC1234", "owner1", false, null);
        room.setId("testId123");
        when(roomRepository.findByRoomId("ABC1234")).thenReturn(Optional.of(room));

        RoomResponse response = roomService.getRoom("ABC1234");
        assertNotNull(response);
        assertEquals("ABC1234", response.getRoomId());
        assertEquals("owner1", response.getOwnerId());
    }

    @Test
    public void testGetRoom_NotFound() {
        when(roomRepository.findByRoomId("INVALID")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> roomService.getRoom("INVALID"));
    }

    @Test
    public void testCreateRoom_Success() {
        when(roomRepository.existsByRoomId("NEWROOM")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            r.setId("generatedId");
            return r;
        });

        RoomCreateRequest request = new RoomCreateRequest("NEWROOM", "user1", false, null);
        RoomResponse response = roomService.createRoom(request);

        assertNotNull(response);
        assertEquals("NEWROOM", response.getRoomId());
        assertEquals("user1", response.getOwnerId());
    }

    @Test
    public void testCreateRoom_DuplicateRoomId() {
        when(roomRepository.existsByRoomId("EXISTING")).thenReturn(true);
        RoomCreateRequest request = new RoomCreateRequest("EXISTING", "user1", false, null);
        assertThrows(BadRequestException.class, () -> roomService.createRoom(request));
    }

    @Test
    public void testPrepareRoomForJoin_CreatesRoomAndRemembersCreator() {
        when(roomRepository.findByRoomId("NEWROOM")).thenReturn(Optional.empty());

        assertTrue(roomService.prepareRoomForJoin("NEWROOM", "creator1"));
        Mockito.verify(roomRepository).save(Mockito.argThat(room ->
                "NEWROOM".equals(room.getRoomId()) && "creator1".equals(room.getOwnerId())));
    }

    @Test
    public void testPrepareRoomForJoin_RejectsClosedRoom() {
        Room room = new Room("CLOSED", "creator1", false, null);
        room.setClosed(true);
        when(roomRepository.findByRoomId("CLOSED")).thenReturn(Optional.of(room));

        assertFalse(roomService.prepareRoomForJoin("CLOSED", "someone-else"));
    }

    @Test
    public void testIsRoomCreator_UsesPersistedRoomOwner() {
        Room room = new Room("ROOM1", "creator1", false, null);
        when(roomRepository.findByRoomId("ROOM1")).thenReturn(Optional.of(room));

        assertTrue(roomService.isRoomCreator("ROOM1", "creator1"));
        assertFalse(roomService.isRoomCreator("ROOM1", "someone-else"));
    }

    @Test
    public void testCloseRoom_OnlyCreatorCanCloseAndRoomDataIsRemoved() {
        Room room = new Room("ROOM1", "creator1", false, null);
        when(roomRepository.findByRoomId("ROOM1")).thenReturn(Optional.of(room));

        assertFalse(roomService.closeRoom("ROOM1", "someone-else"));
        assertTrue(roomService.closeRoom("ROOM1", "creator1"));
        assertTrue(room.isClosed());
        Mockito.verify(messageRepository).deleteByRoomId("ROOM1");
        Mockito.verify(pinnedNoteRepository).deleteByRoomId("ROOM1");
        Mockito.verify(roomMediaStateRepository).deleteById("ROOM1");
    }
}
