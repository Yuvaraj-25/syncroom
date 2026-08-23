package com.project.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.service.AiAssistantService;
import com.project.service.RoomStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SocketIoWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SocketIoWebSocketHandler.class);

    // socketId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // socketId -> current roomId
    private final Map<String, String> socketToRoom = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final RoomStateManager roomStateManager;
    private final AiAssistantService aiAssistantService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public SocketIoWebSocketHandler(ObjectMapper objectMapper,
                                    RoomStateManager roomStateManager,
                                    AiAssistantService aiAssistantService) {
        this.objectMapper = objectMapper;
        this.roomStateManager = roomStateManager;
        this.aiAssistantService = aiAssistantService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String socketId = session.getId();
        sessions.put(socketId, session);
        log.info("Socket.IO client connected: socketId={}", socketId);

        // Engine.IO Open Packet (0)
        Map<String, Object> openPayload = new HashMap<>();
        openPayload.put("sid", socketId);
        openPayload.put("upgrades", Collections.emptyList());
        openPayload.put("pingInterval", 25000);
        openPayload.put("pingTimeout", 20000);
        openPayload.put("maxPayload", 1000000);

        String engineIoOpen = "0" + objectMapper.writeValueAsString(openPayload);
        session.sendMessage(new TextMessage(engineIoOpen));

        // Socket.IO Connect Packet (40)
        Map<String, Object> connectPayload = Map.of("sid", socketId);
        String socketIoConnect = "40" + objectMapper.writeValueAsString(connectPayload);
        session.sendMessage(new TextMessage(socketIoConnect));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;

        // Engine.IO PING (2) -> PONG (3)
        if ("2".equals(payload)) {
            session.sendMessage(new TextMessage("3"));
            return;
        }

        // Engine.IO PONG (3)
        if ("3".equals(payload)) {
            return;
        }

        // Socket.IO Message Packet starts with "42" (or "42/," etc.)
        if (payload.startsWith("42")) {
            String jsonPart = payload.substring(2);
            // Handle possible namespace like "42/admin,["event", ...]"
            if (jsonPart.startsWith("/")) {
                int commaIdx = jsonPart.indexOf(",");
                if (commaIdx != -1) {
                    jsonPart = jsonPart.substring(commaIdx + 1);
                }
            }

            try {
                JsonNode root = objectMapper.readTree(jsonPart);
                if (root.isArray() && root.size() >= 1) {
                    String eventName = root.get(0).asText();
                    JsonNode dataNode = root.size() > 1 ? root.get(1) : null;
                    handleSocketEvent(session, eventName, dataNode);
                }
            } catch (Exception ex) {
                log.error("Failed to parse Socket.IO event payload: {}", payload, ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSocketEvent(WebSocketSession session, String event, JsonNode data) throws Exception {
        String socketId = session.getId();
        log.debug("Received event '{}' from socketId={}", event, socketId);

        Map<String, Object> dataMap = data != null && data.isObject()
                ? objectMapper.convertValue(data, Map.class)
                : Collections.emptyMap();

        String roomId = dataMap.get("roomId") != null ? String.valueOf(dataMap.get("roomId")) : socketToRoom.get(socketId);

        switch (event) {
            case "join-room" -> handleJoinRoom(session, dataMap);
            case "video-play" -> handleVideoPlay(socketId, roomId, dataMap);
            case "video-pause" -> handleVideoPause(socketId, roomId, dataMap);
            case "video-seek" -> handleVideoSeek(socketId, roomId, dataMap);
            case "video-url-change" -> handleVideoUrlChange(socketId, roomId, dataMap);
            case "add-to-queue" -> handleAddToQueue(roomId, dataMap);
            case "remove-from-queue" -> handleRemoveFromQueue(socketId, roomId, dataMap);
            case "play-next" -> handlePlayNext(socketId, roomId);
            case "draw-line" -> broadcastToOthersInRoom(roomId, socketId, "draw-line", dataMap);
            case "clear-whiteboard" -> broadcastToOthersInRoom(roomId, socketId, "clear-whiteboard", Collections.emptyMap());
            case "request-whiteboard" -> handleRequestWhiteboard(socketId, roomId);
            case "sync-whiteboard-response" -> handleSyncWhiteboardResponse(dataMap);
            case "emoji-reaction" -> handleEmojiReaction(socketId, roomId, dataMap);
            case "set-room-name" -> handleSetRoomName(socketId, roomId, dataMap);
            case "request-sync-state" -> broadcastToOthersInRoom(roomId, socketId, "sync-state-request", Map.of("requesterId", socketId));
            case "sync-state-response" -> handleSyncStateResponse(dataMap);
            case "chat-message" -> handleChatMessage(socketId, roomId, dataMap);
            case "typing" -> broadcastToOthersInRoom(roomId, socketId, "typing", dataMap);
            case "toggle-mic" -> handleToggleMic(roomId, dataMap);
            case "toggle-camera" -> handleToggleCamera(roomId, dataMap);
            case "update-profile" -> handleUpdateProfile(socketId, roomId, dataMap);
            case "kick-user" -> handleKickUser(socketId, roomId, dataMap);
            case "force-mute" -> handleForceMute(socketId, roomId, dataMap);
            case "force-mute-all" -> handleForceMuteAll(socketId, roomId);
            case "toggle-hand" -> handleToggleHand(socketId, roomId, dataMap);
            case "lower-all-hands" -> handleLowerAllHands(socketId, roomId);
            case "grant-owner" -> handleGrantOwner(socketId, roomId, dataMap);
            case "request-owner" -> handleRequestOwner(socketId, roomId, dataMap);
            case "accept-owner-request" -> handleAcceptOwnerRequest(socketId, roomId, dataMap);
            case "webrtc-offer" -> handleWebRtcSignaling("webrtc-offer", socketId, dataMap);
            case "webrtc-answer" -> handleWebRtcSignaling("webrtc-answer", socketId, dataMap);
            case "webrtc-ice-candidate" -> handleWebRtcSignaling("webrtc-ice-candidate", socketId, dataMap);
            case "create-poll" -> handleCreatePoll(roomId, dataMap);
            case "vote-poll" -> handleVotePoll(roomId, dataMap);
            case "close-poll" -> handleClosePoll(roomId, dataMap);
            case "delete-poll" -> handleDeletePoll(roomId, dataMap);
            case "submit-question" -> handleSubmitQuestion(roomId, dataMap);
            case "upvote-question" -> handleUpvoteQuestion(roomId, dataMap);
            case "answer-question" -> handleAnswerQuestion(roomId, dataMap);
            case "delete-question" -> handleDeleteQuestion(roomId, dataMap);
            default -> log.debug("Unhandled event: {}", event);
        }
    }

    // ── Handler Implementations ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleJoinRoom(WebSocketSession session, Map<String, Object> dataMap) {
        String socketId = session.getId();
        String roomId = String.valueOf(dataMap.get("roomId"));
        Map<String, Object> rawUser = (Map<String, Object>) dataMap.get("user");

        socketToRoom.put(socketId, roomId);
        Map<String, Object> enhancedUser = roomStateManager.joinRoom(roomId, socketId, rawUser);

        // Broadcast user-joined to other users in room
        broadcastToOthersInRoom(roomId, socketId, "user-joined", Map.of("socketId", socketId, "user", enhancedUser));

        // Send current users in room to the newly joined user
        List<Map<String, Object>> usersInRoom = roomStateManager.getUsersInRoom(roomId);
        emitToSocket(socketId, "room-users", usersInRoom);

        // Send current queue
        emitToSocket(socketId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));

        // Send current polls and Q&A
        emitToSocket(socketId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
        emitToSocket(socketId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
    }

    private void handleVideoPlay(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            broadcastToOthersInRoom(roomId, socketId, "video-play", dataMap);
        }
    }

    private void handleVideoPause(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            broadcastToOthersInRoom(roomId, socketId, "video-pause", dataMap);
        }
    }

    private void handleVideoSeek(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            broadcastToOthersInRoom(roomId, socketId, "video-seek", dataMap);
        }
    }

    private void handleVideoUrlChange(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            emitToRoom(roomId, "video-url-change", dataMap);
        }
    }

    private void handleAddToQueue(String roomId, Map<String, Object> dataMap) {
        Object item = dataMap.get("videoInfo") != null ? dataMap.get("videoInfo") : dataMap.get("videoUrl");
        if (item != null) {
            roomStateManager.addToQueue(roomId, item);
            emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
        }
    }

    private void handleRemoveFromQueue(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            int index = Integer.parseInt(String.valueOf(dataMap.get("index")));
            roomStateManager.removeFromQueue(roomId, index);
            emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePlayNext(String socketId, String roomId) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            Object nextItem = roomStateManager.playNext(roomId);
            if (nextItem != null) {
                String newUrl = nextItem instanceof Map
                        ? String.valueOf(((Map<String, Object>) nextItem).get("url"))
                        : String.valueOf(nextItem);

                emitToRoom(roomId, "video-url-change", Map.of("newUrl", newUrl));
                emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
            }
        }
    }

    private void handleRequestWhiteboard(String socketId, String roomId) {
        String primaryOwnerSocketId = roomStateManager.getPrimaryOwnerSocketId(roomId);
        if (primaryOwnerSocketId != null) {
            emitToSocket(primaryOwnerSocketId, "sync-whiteboard-request", Map.of("requesterId", socketId));
        }
    }

    private void handleSyncWhiteboardResponse(Map<String, Object> dataMap) {
        String requesterId = String.valueOf(dataMap.get("requesterId"));
        if (requesterId != null) {
            emitToSocket(requesterId, "sync-whiteboard-response", Map.of("dataUrl", dataMap.get("dataUrl")));
        }
    }

    private void handleEmojiReaction(String socketId, String roomId, Map<String, Object> dataMap) {
        Map<String, Object> payload = new HashMap<>(dataMap);
        payload.put("senderId", socketId);
        emitToRoom(roomId, "emoji-reaction", payload);
    }

    private void handleSetRoomName(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            emitToRoom(roomId, "room-name-changed", dataMap);
        }
    }

    private void handleSyncStateResponse(Map<String, Object> dataMap) {
        String requesterId = String.valueOf(dataMap.get("requesterId"));
        if (requesterId != null) {
            emitToSocket(requesterId, "sync-state-response", dataMap);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleChatMessage(String socketId, String roomId, Map<String, Object> dataMap) {
        Object msgObj = dataMap.get("message");
        if (msgObj == null) return;

        emitToRoom(roomId, "chat-message", msgObj);

        if (msgObj instanceof Map) {
            Map<String, Object> msg = (Map<String, Object>) msgObj;
            String text = String.valueOf(msg.getOrDefault("text", ""));
            if (aiAssistantService.isAiTrigger(text)) {
                emitToRoom(roomId, "typing", Map.of("userName", "SyncRoom AI 🤖"));

                scheduler.schedule(() -> {
                    Map<String, Object> aiResponse = aiAssistantService.generateAiResponse(text);
                    emitToRoom(roomId, "chat-message", aiResponse);
                }, 400, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void handleToggleMic(String roomId, Map<String, Object> dataMap) {
        String userId = String.valueOf(dataMap.get("userId"));
        boolean isMuted = Boolean.TRUE.equals(dataMap.get("isMuted"));
        roomStateManager.updateUserField(roomId, userId, "isMuted", isMuted);
        broadcastToOthersInRoom(roomId, userId, "user-toggled-mic", dataMap);
    }

    private void handleToggleCamera(String roomId, Map<String, Object> dataMap) {
        String userId = String.valueOf(dataMap.get("userId"));
        boolean isVideoOn = Boolean.TRUE.equals(dataMap.get("isVideoOn"));
        roomStateManager.updateUserField(roomId, userId, "isVideoOn", isVideoOn);
        broadcastToOthersInRoom(roomId, userId, "user-toggled-camera", dataMap);
    }

    private void handleUpdateProfile(String socketId, String roomId, Map<String, Object> dataMap) {
        if (dataMap.get("name") != null) {
            roomStateManager.updateUserField(roomId, socketId, "name", dataMap.get("name"));
        }
        if (dataMap.get("image") != null) {
            roomStateManager.updateUserField(roomId, socketId, "image", dataMap.get("image"));
        }
        Map<String, Object> payload = new HashMap<>(dataMap);
        payload.put("socketId", socketId);
        emitToRoom(roomId, "user-profile-updated", payload);
    }

    private void handleKickUser(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            String targetSocketId = String.valueOf(dataMap.get("targetSocketId"));
            emitToSocket(targetSocketId, "kicked", Collections.emptyMap());
        }
    }

    private void handleForceMute(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            String targetSocketId = String.valueOf(dataMap.get("targetSocketId"));
            emitToSocket(targetSocketId, "force-mute", Collections.emptyMap());
        }
    }

    private void handleForceMuteAll(String socketId, String roomId) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            Set<String> socketIds = roomStateManager.getSocketIdsInRoom(roomId);
            for (String sid : socketIds) {
                if (!sid.equals(socketId)) {
                    emitToSocket(sid, "force-mute", Collections.emptyMap());
                }
            }
        }
    }

    private void handleToggleHand(String socketId, String roomId, Map<String, Object> dataMap) {
        boolean isHandRaised = Boolean.TRUE.equals(dataMap.get("isHandRaised"));
        roomStateManager.updateUserField(roomId, socketId, "isHandRaised", isHandRaised);
        broadcastToOthersInRoom(roomId, socketId, "hand-toggled", Map.of("userId", socketId, "isHandRaised", isHandRaised));
    }

    private void handleLowerAllHands(String socketId, String roomId) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            roomStateManager.lowerAllHands(roomId);
            emitToRoom(roomId, "hands-lowered", Collections.emptyMap());
        }
    }

    private void handleGrantOwner(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            String targetSocketId = String.valueOf(dataMap.get("targetSocketId"));
            roomStateManager.grantOwner(roomId, targetSocketId);
            emitToRoom(roomId, "owner-status-update", Map.of("socketId", targetSocketId, "isOwner", true));
        }
    }

    private void handleRequestOwner(String socketId, String roomId, Map<String, Object> dataMap) {
        String primaryOwnerSocketId = roomStateManager.getPrimaryOwnerSocketId(roomId);
        if (primaryOwnerSocketId != null) {
            emitToSocket(primaryOwnerSocketId, "owner-request", Map.of(
                    "socketId", socketId,
                    "userName", String.valueOf(dataMap.get("userName"))
            ));
        }
    }

    private void handleAcceptOwnerRequest(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserPrimaryOwner(roomId, socketId)) {
            String targetSocketId = String.valueOf(dataMap.get("targetSocketId"));
            roomStateManager.grantOwner(roomId, targetSocketId);
            emitToRoom(roomId, "owner-status-update", Map.of("socketId", targetSocketId, "isOwner", true));
        }
    }

    private void handleWebRtcSignaling(String event, String socketId, Map<String, Object> dataMap) {
        String to = String.valueOf(dataMap.get("to"));
        if (to != null && !to.isBlank()) {
            Map<String, Object> payload = new HashMap<>(dataMap);
            payload.put("from", socketId);
            emitToSocket(to, event, payload);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCreatePoll(String roomId, Map<String, Object> dataMap) {
        Map<String, Object> poll = (Map<String, Object>) dataMap.get("poll");
        if (poll != null) {
            roomStateManager.createPoll(roomId, poll);
            emitToRoom(roomId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
            emitToRoom(roomId, "new-poll", Map.of("poll", poll));
        }
    }

    private void handleVotePoll(String roomId, Map<String, Object> dataMap) {
        String pollId = String.valueOf(dataMap.get("pollId"));
        Object optionIndex = dataMap.get("optionIndex");
        String userId = String.valueOf(dataMap.get("userId"));
        roomStateManager.votePoll(roomId, pollId, optionIndex, userId);
        emitToRoom(roomId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
    }

    private void handleClosePoll(String roomId, Map<String, Object> dataMap) {
        String pollId = String.valueOf(dataMap.get("pollId"));
        roomStateManager.closePoll(roomId, pollId);
        emitToRoom(roomId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
    }

    private void handleDeletePoll(String roomId, Map<String, Object> dataMap) {
        String pollId = String.valueOf(dataMap.get("pollId"));
        roomStateManager.deletePoll(roomId, pollId);
        emitToRoom(roomId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
    }

    @SuppressWarnings("unchecked")
    private void handleSubmitQuestion(String roomId, Map<String, Object> dataMap) {
        Map<String, Object> question = (Map<String, Object>) dataMap.get("question");
        if (question != null) {
            roomStateManager.submitQuestion(roomId, question);
            emitToRoom(roomId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
        }
    }

    private void handleUpvoteQuestion(String roomId, Map<String, Object> dataMap) {
        String questionId = String.valueOf(dataMap.get("questionId"));
        String userId = String.valueOf(dataMap.get("userId"));
        roomStateManager.upvoteQuestion(roomId, questionId, userId);
        emitToRoom(roomId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
    }

    private void handleAnswerQuestion(String roomId, Map<String, Object> dataMap) {
        String questionId = String.valueOf(dataMap.get("questionId"));
        Object answer = dataMap.get("answer");
        roomStateManager.answerQuestion(roomId, questionId, answer);
        emitToRoom(roomId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
    }

    private void handleDeleteQuestion(String roomId, Map<String, Object> dataMap) {
        String questionId = String.valueOf(dataMap.get("questionId"));
        roomStateManager.deleteQuestion(roomId, questionId);
        emitToRoom(roomId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String socketId = session.getId();
        sessions.remove(socketId);
        socketToRoom.remove(socketId);

        RoomStateManager.DisconnectResult result = roomStateManager.removeUserFromAllRooms(socketId);
        if (result != null) {
            String roomId = result.getRoomId();
            broadcastToOthersInRoom(roomId, socketId, "user-left", Map.of("socketId", socketId, "user", result.getUser()));

            if (result.getNewPrimarySocketId() != null) {
                emitToRoom(roomId, "owner-status-update", Map.of(
                        "socketId", result.getNewPrimarySocketId(),
                        "isOwner", true,
                        "isPrimaryOwner", true
                ));
            }
        }

        log.info("Socket.IO client disconnected: socketId={}", socketId);
    }

    // ── Emission Helpers ─────────────────────────────────────────────────────

    public void emitToSocket(String socketId, String event, Object data) {
        WebSocketSession session = sessions.get(socketId);
        if (session != null && session.isOpen()) {
            try {
                String payload = "42" + objectMapper.writeValueAsString(List.of(event, data));
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.error("Failed to emit event '{}' to socketId={}", event, socketId, e);
            }
        }
    }

    public void emitToRoom(String roomId, String event, Object data) {
        Set<String> socketIds = roomStateManager.getSocketIdsInRoom(roomId);
        for (String socketId : socketIds) {
            emitToSocket(socketId, event, data);
        }
    }

    public void broadcastToOthersInRoom(String roomId, String senderSocketId, String event, Object data) {
        Set<String> socketIds = roomStateManager.getSocketIdsInRoom(roomId);
        for (String socketId : socketIds) {
            if (!socketId.equals(senderSocketId)) {
                emitToSocket(socketId, event, data);
            }
        }
    }
}
