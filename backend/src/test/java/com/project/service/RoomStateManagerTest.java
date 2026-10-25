package com.project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RoomStateManagerTest {

    private RoomStateManager roomStateManager;

    @BeforeEach
    void setUp() {
        roomStateManager = new RoomStateManager();
    }

    @Test
    void testJoinRoomAndOwnership() {
        String roomId = "test-room-1";
        Map<String, Object> user1 = new HashMap<>();
        user1.put("name", "Alice");

        Map<String, Object> enhancedUser1 = roomStateManager.joinRoom(roomId, "socket-1", user1);
        assertTrue(Boolean.TRUE.equals(enhancedUser1.get("isOwner")));
        assertTrue(Boolean.TRUE.equals(enhancedUser1.get("isPrimaryOwner")));
        assertEquals("socket-1", roomStateManager.getPrimaryOwnerSocketId(roomId));

        Map<String, Object> user2 = new HashMap<>();
        user2.put("name", "Bob");
        Map<String, Object> enhancedUser2 = roomStateManager.joinRoom(roomId, "socket-2", user2);
        assertFalse(Boolean.TRUE.equals(enhancedUser2.get("isOwner")));
        assertFalse(Boolean.TRUE.equals(enhancedUser2.get("isPrimaryOwner")));

        List<Map<String, Object>> usersInRoom = roomStateManager.getUsersInRoom(roomId);
        assertEquals(2, usersInRoom.size());
    }

    @Test
    void testVideoStateTracking() {
        String roomId = "video-room";
        roomStateManager.updateVideoUrl(roomId, "https://youtube.com/watch?v=12345");
        roomStateManager.updateVideoPlay(roomId, 15.0);

        RoomStateManager.VideoState state = roomStateManager.getVideoState(roomId);
        assertNotNull(state);
        assertEquals("https://youtube.com/watch?v=12345", state.getUrl());
        assertTrue(state.isPlaying());
        assertTrue(state.getCalculatedCurrentTime() >= 15.0);

        roomStateManager.updateVideoPause(roomId, 20.0);
        state = roomStateManager.getVideoState(roomId);
        assertFalse(state.isPlaying());
        assertEquals(20.0, state.getCurrentTime());
    }

    @Test
    void playingWithoutSelectedVideoUsesDefaultVideo() {
        roomStateManager.updateVideoPlay("empty-video-room", 0.0);

        assertNotNull(roomStateManager.getVideoState("empty-video-room"));
        assertEquals("aqz-KE-bpKQ", roomStateManager.getVideoState("empty-video-room").getUrl());
    }

    @Test
    void mediaQueueAndHistoryRemainAfterLastParticipantLeaves() {
        String roomId = "persistent-media-room";
        roomStateManager.joinRoom(roomId, "socket-1", Map.of("name", "Alice"));
        roomStateManager.updateVideoUrl(roomId, "https://www.youtube.com/watch?v=video12345");
        roomStateManager.addToQueue(roomId, Map.of(
                "url", "https://www.youtube.com/watch?v=video67890",
                "title", "Next video"
        ));
        roomStateManager.addVideoHistory(roomId, Map.of(
                "url", "https://www.youtube.com/watch?v=video00000",
                "title", "Previous video"
        ));

        roomStateManager.removeUserFromAllRooms("socket-1");

        assertEquals("https://www.youtube.com/watch?v=video12345",
                roomStateManager.getVideoState(roomId).getUrl());
        assertEquals(1, roomStateManager.getQueueSnapshot(roomId).size());
        assertEquals("https://www.youtube.com/watch?v=video00000",
                roomStateManager.getVideoHistorySnapshot(roomId).get(0).get("url"));
    }

    @Test
    void chatHistoryRemainsAvailableAfterLastParticipantLeaves() {
        String roomId = "persistent-chat-room";
        roomStateManager.joinRoom(roomId, "socket-1", Map.of("name", "Alice"));
        roomStateManager.addRoomMessage(roomId, Map.of(
                "id", "message-1",
                "userName", "Alice",
                "text", "Hello"
        ));

        roomStateManager.removeUserFromAllRooms("socket-1");

        List<Map<String, Object>> history = roomStateManager.getRoomMessages(roomId);
        assertEquals(1, history.size());
        assertEquals("Hello", history.get(0).get("text"));
    }

    @Test
    void removeVideoHistoryDeletesOnlyMatchingVideo() {
        String roomId = "video-history-room";
        roomStateManager.addVideoHistory(roomId, Map.of("url", "video-a", "title", "A"));
        roomStateManager.addVideoHistory(roomId, Map.of("url", "video-b", "title", "B"));

        roomStateManager.removeVideoHistory(roomId, "video-a");

        List<Map<String, Object>> history = roomStateManager.getVideoHistorySnapshot(roomId);
        assertEquals(1, history.size());
        assertEquals("video-b", history.get(0).get("url"));
    }

    @Test
    void clearVideoHistoryRemovesAllHistoryWithoutRemovingQueue() {
        String roomId = "clear-video-history-room";
        roomStateManager.addVideoHistory(roomId, Map.of("url", "video-a", "title", "A"));
        roomStateManager.getQueue(roomId).add("video-next");

        roomStateManager.clearVideoHistory(roomId);

        assertTrue(roomStateManager.getVideoHistorySnapshot(roomId).isEmpty());
        assertEquals(List.of("video-next"), roomStateManager.getQueueSnapshot(roomId));
    }

    @Test
    void testWhiteboardStrokes() {
        String roomId = "wb-room";
        Map<String, Object> stroke1 = Map.of("x0", 10, "y0", 20, "x1", 30, "y1", 40, "color", "#fff", "width", 2);
        roomStateManager.addWhiteboardStroke(roomId, stroke1);

        List<Map<String, Object>> strokes = roomStateManager.getWhiteboardStrokes(roomId);
        assertEquals(1, strokes.size());
        assertEquals(10, strokes.get(0).get("x0"));

        roomStateManager.clearWhiteboardStrokes(roomId);
        assertTrue(roomStateManager.getWhiteboardStrokes(roomId).isEmpty());
    }

    @Test
    void testPollsAndVoting() {
        String roomId = "poll-room";
        Map<String, Object> poll = new HashMap<>();
        poll.put("id", "poll-1");
        poll.put("question", "What to watch?");
        poll.put("options", List.of("Movie A", "Movie B"));
        poll.put("votes", new HashMap<String, List<String>>());
        poll.put("isOpen", true);

        roomStateManager.createPoll(roomId, poll);
        assertEquals(1, roomStateManager.getPolls(roomId).size());

        // Vote for option 0
        roomStateManager.votePoll(roomId, "poll-1", 0, "user-123");
        List<Map<String, Object>> polls = roomStateManager.getPolls(roomId);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> votes = (Map<String, List<String>>) polls.get(0).get("votes");
        assertTrue(votes.get("0").contains("user-123"));

        // Change vote to option 1
        roomStateManager.votePoll(roomId, "poll-1", 1, "user-123");
        assertFalse(votes.get("0").contains("user-123"));
        assertTrue(votes.get("1").contains("user-123"));

        // Close poll
        roomStateManager.closePoll(roomId, "poll-1");
        assertEquals(false, polls.get(0).get("isOpen"));
    }

    @Test
    void testQAUplink() {
        String roomId = "qa-room";
        Map<String, Object> question = new HashMap<>();
        question.put("id", "q-1");
        question.put("text", "When does the stream start?");

        roomStateManager.submitQuestion(roomId, question);
        assertEquals(1, roomStateManager.getQuestions(roomId).size());

        roomStateManager.upvoteQuestion(roomId, "q-1", "user-1");
        roomStateManager.answerQuestion(roomId, "q-1", Map.of("text", "At 8 PM"));

        List<Map<String, Object>> questions = roomStateManager.getQuestions(roomId);
        @SuppressWarnings("unchecked")
        List<String> upvotes = (List<String>) questions.get(0).get("upvotes");
        assertTrue(upvotes.contains("user-1"));

        @SuppressWarnings("unchecked")
        List<Object> answers = (List<Object>) questions.get(0).get("answers");
        assertEquals(1, answers.size());
    }

    @Test
    void testRoomNameAndDisconnectHandover() {
        String roomId = "room-meta";
        roomStateManager.setRoomName(roomId, "Movie Night 🍿");
        assertEquals("Movie Night 🍿", roomStateManager.getRoomName(roomId));

        roomStateManager.joinRoom(roomId, "socket-primary", Map.of("name", "Host"));
        roomStateManager.joinRoom(roomId, "socket-guest", Map.of("name", "Guest"));

        RoomStateManager.DisconnectResult result = roomStateManager.removeUserFromAllRooms("socket-primary");
        assertNotNull(result);
        assertEquals(roomId, result.getRoomId());
        assertEquals("socket-guest", result.getNewPrimarySocketId());
        assertTrue(roomStateManager.isUserPrimaryOwner(roomId, "socket-guest"));
    }

    @Test
    void closingRoomRemovesAllLiveRoomStateAndReturnsConnectedSockets() {
        String roomId = "closed-room";
        roomStateManager.joinRoom(roomId, "socket-host", Map.of("name", "Host"));
        roomStateManager.addToQueue(roomId, "next-video");
        roomStateManager.addVideoHistory(roomId, Map.of("url", "old-video"));
        roomStateManager.addRoomMessage(roomId, Map.of("id", "message-1"));

        assertEquals(java.util.Set.of("socket-host"), roomStateManager.closeRoom(roomId));

        assertTrue(roomStateManager.getUsersInRoom(roomId).isEmpty());
        assertNull(roomStateManager.getRoomName(roomId));
        assertNull(roomStateManager.getVideoState(roomId));
        assertTrue(roomStateManager.getQueueSnapshot(roomId).isEmpty());
        assertTrue(roomStateManager.getVideoHistorySnapshot(roomId).isEmpty());
        assertTrue(roomStateManager.getRoomMessages(roomId).isEmpty());
    }
}
