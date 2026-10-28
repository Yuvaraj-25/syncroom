package com.project.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.project.service.AiAssistantService;
import com.project.service.RoomStateManager;
import com.project.model.Message;
import com.project.model.PinnedNote;
import com.project.model.RoomMediaState;
import com.project.repository.MessageRepository;
import com.project.repository.PinnedNoteRepository;
import com.project.repository.RoomMediaStateRepository;
import com.project.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
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
    private final MessageRepository messageRepository;
    private final PinnedNoteRepository pinnedNoteRepository;
    private final RoomMediaStateRepository roomMediaStateRepository;
    private final RoomService roomService;
    private final Object roomLifecycleLock = new Object();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public SocketIoWebSocketHandler(ObjectMapper objectMapper,
                                    RoomStateManager roomStateManager,
                                    AiAssistantService aiAssistantService,
                                    MessageRepository messageRepository,
                                    PinnedNoteRepository pinnedNoteRepository,
                                    RoomMediaStateRepository roomMediaStateRepository,
                                    RoomService roomService) {
        this.objectMapper = objectMapper;
        this.roomStateManager = roomStateManager;
        this.aiAssistantService = aiAssistantService;
        this.messageRepository = messageRepository;
        this.pinnedNoteRepository = pinnedNoteRepository;
        this.roomMediaStateRepository = roomMediaStateRepository;
        this.roomService = roomService;
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
            case "delete-room" -> handleDeleteRoom(socketId, roomId);
            case "video-play" -> handleVideoPlay(socketId, roomId, dataMap);
            case "video-pause" -> handleVideoPause(socketId, roomId, dataMap);
            case "video-seek" -> handleVideoSeek(socketId, roomId, dataMap);
            case "video-url-change" -> handleVideoUrlChange(socketId, roomId, dataMap);
            case "add-to-queue" -> handleAddToQueue(roomId, dataMap);
            case "remove-from-queue" -> handleRemoveFromQueue(socketId, roomId, dataMap);
            case "remove-video-history" -> handleRemoveVideoHistory(socketId, roomId, dataMap);
            case "clear-video-history" -> handleClearVideoHistory(socketId, roomId);
            case "play-next" -> handlePlayNext(socketId, roomId);
            case "draw-line" -> handleDrawLine(socketId, roomId, dataMap);
            case "clear-whiteboard" -> handleClearWhiteboard(socketId, roomId);
            case "request-whiteboard" -> handleRequestWhiteboard(socketId, roomId);
            case "sync-whiteboard-response" -> handleSyncWhiteboardResponse(dataMap);
            case "emoji-reaction" -> handleEmojiReaction(socketId, roomId, dataMap);
            case "set-room-name" -> handleSetRoomName(socketId, roomId, dataMap);
            case "request-sync-state" -> broadcastToOthersInRoom(roomId, socketId, "sync-state-request", Map.of("requesterId", socketId));
            case "sync-state-response" -> handleSyncStateResponse(dataMap);
            case "chat-message" -> handleChatMessage(socketId, roomId, dataMap);
            case "edit-chat-message" -> handleEditChatMessage(socketId, roomId, dataMap);
            case "delete-chat-message" -> handleDeleteChatMessage(socketId, roomId, dataMap);
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
            case "webrtc-renegotiate" -> handleWebRtcSignaling("webrtc-renegotiate", socketId, dataMap);
            case "create-poll" -> handleCreatePoll(roomId, dataMap);
            case "vote-poll" -> handleVotePoll(roomId, dataMap);
            case "close-poll" -> handleClosePoll(roomId, dataMap);
            case "delete-poll" -> handleDeletePoll(roomId, dataMap);
            case "submit-question" -> handleSubmitQuestion(roomId, dataMap);
            case "upvote-question" -> handleUpvoteQuestion(roomId, dataMap);
            case "answer-question" -> handleAnswerQuestion(roomId, dataMap);
            case "delete-question" -> handleDeleteQuestion(roomId, dataMap);
            case "pin-message" -> handlePinMessage(roomId, dataMap);
            case "unpin-message" -> handleUnpinMessage(roomId, dataMap);
            case "request-pinned-notes" -> handleRequestPinnedNotes(socketId, roomId);
            case "file-share" -> handleFileShare(socketId, roomId, dataMap);
            case "request-files" -> handleRequestFiles(socketId, roomId);
            default -> log.debug("Unhandled event: {}", event);
        }
    }

    // ── Handler Implementations ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleJoinRoom(WebSocketSession session, Map<String, Object> dataMap) {
        String socketId = session.getId();
        String roomId = Objects.toString(dataMap.get("roomId"), "").trim();
        Map<String, Object> rawUser = (Map<String, Object>) dataMap.get("user");
        String ownerId = rawUser == null ? "" : Objects.toString(rawUser.get("userId"), "");

        Map<String, Object> enhancedUser;
        synchronized (roomLifecycleLock) {
            if (roomId.equals(socketToRoom.get(socketId))) {
                emitToSocket(socketId, "room-users", roomStateManager.getUsersInRoom(roomId));
                return;
            }

            if (!roomService.prepareRoomForJoin(roomId, ownerId)) {
                emitToSocket(socketId, "room-closed", Map.of("roomId", roomId));
                try {
                    session.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    log.warn("Could not close rejected room connection for socketId={}", socketId, e);
                }
                return;
            }

            socketToRoom.put(socketId, roomId);
            enhancedUser = roomStateManager.joinRoom(roomId, socketId, rawUser);
            enhancedUser.put("isCreator", roomService.isRoomCreator(roomId, ownerId));
        }
        restoreMediaState(roomId);

        // Broadcast user-joined to other users in room
        broadcastToOthersInRoom(roomId, socketId, "user-joined", Map.of("socketId", socketId, "user", enhancedUser));

        // Send current users in room to the newly joined user
        List<Map<String, Object>> usersInRoom = roomStateManager.getUsersInRoom(roomId);
        emitToSocket(socketId, "room-users", usersInRoom);

        // Send custom room name if set
        String roomName = roomStateManager.getRoomName(roomId);
        if (roomName != null && !roomName.isBlank()) {
            emitToSocket(socketId, "room-name-changed", Map.of("name", roomName));
        }

        // Send current video playback synchronization state
        RoomStateManager.VideoState videoState = roomStateManager.getVideoState(roomId);
        if (videoState != null) {
            if (videoState.getUrl() != null && !videoState.getUrl().isBlank()) {
                emitToSocket(socketId, "video-url-change", Map.of("newUrl", videoState.getUrl()));
            }
            double calcTime = videoState.getCalculatedCurrentTime();
            if (videoState.isPlaying()) {
                emitToSocket(socketId, "video-play", Map.of("time", calcTime));
            } else {
                emitToSocket(socketId, "video-pause", Map.of("time", calcTime));
            }
        }

        // Send current queue
        emitToSocket(socketId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
        emitToSocket(socketId, "video-history-updated",
                Map.of("history", roomStateManager.getVideoHistorySnapshot(roomId)));

        // Send whiteboard stroke history
        List<Map<String, Object>> strokes = roomStateManager.getWhiteboardStrokes(roomId);
        if (strokes != null && !strokes.isEmpty()) {
            synchronized (strokes) {
                for (Map<String, Object> stroke : strokes) {
                    emitToSocket(socketId, "draw-line", stroke);
                }
            }
        }

        // Send current polls and Q&A
        emitToSocket(socketId, "polls-updated", Map.of("polls", roomStateManager.getPolls(roomId)));
        emitToSocket(socketId, "qa-updated", Map.of("questions", roomStateManager.getQuestions(roomId)));
        emitToSocket(socketId, "chat-history", Map.of("messages", loadChatHistory(roomId)));
        emitToSocket(socketId, "pinned-notes-updated", Map.of("notes", loadPinnedNotes(roomId)));
    }

    private void handleDeleteRoom(String socketId, String roomId) {
        Map<String, Object> user = roomStateManager.getUser(roomId, socketId);
        String ownerId = user == null ? "" : Objects.toString(user.get("userId"), "");
        Set<String> roomSocketIds;

        synchronized (roomLifecycleLock) {
            if (!roomService.closeRoom(roomId, ownerId)) {
                emitToSocket(socketId, "room-delete-error",
                        Map.of("message", "Only the person who created this room can delete it."));
                return;
            }

            emitToRoom(roomId, "room-closed", Map.of("roomId", roomId));
            roomSocketIds = roomStateManager.closeRoom(roomId);
        }

        for (String roomSocketId : roomSocketIds) {
            WebSocketSession roomSession = sessions.get(roomSocketId);
            if (roomSession != null && roomSession.isOpen()) {
                try {
                    roomSession.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    log.warn("Could not close deleted room connection for socketId={}", roomSocketId, e);
                }
            }
        }
    }

    private void handleVideoPlay(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            Double time = null;
            if (dataMap.get("time") instanceof Number num) {
                time = num.doubleValue();
            }
            roomStateManager.updateVideoPlay(roomId, time);
            persistMediaState(roomId);
            broadcastToOthersInRoom(roomId, socketId, "video-play", dataMap);
        }
    }

    private void handleVideoPause(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            Double time = null;
            if (dataMap.get("time") instanceof Number num) {
                time = num.doubleValue();
            }
            roomStateManager.updateVideoPause(roomId, time);
            persistMediaState(roomId);
            broadcastToOthersInRoom(roomId, socketId, "video-pause", dataMap);
        }
    }

    private void handleVideoSeek(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            double time = 0.0;
            if (dataMap.get("time") instanceof Number num) {
                time = num.doubleValue();
            }
            roomStateManager.updateVideoSeek(roomId, time);
            persistMediaState(roomId);
            broadcastToOthersInRoom(roomId, socketId, "video-seek", dataMap);
        }
    }

    private void handleVideoUrlChange(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            String newUrl = String.valueOf(dataMap.get("newUrl"));
            recordCurrentVideoInHistory(roomId, newUrl);
            roomStateManager.updateVideoUrl(roomId, newUrl);
            persistMediaState(roomId);
            emitToRoom(roomId, "video-url-change", dataMap);
            emitVideoHistory(roomId);
        }
    }

    private void handleAddToQueue(String roomId, Map<String, Object> dataMap) {
        Object item = dataMap.get("videoInfo") != null ? dataMap.get("videoInfo") : dataMap.get("videoUrl");
        if (item != null) {
            roomStateManager.addToQueue(roomId, item);
            persistMediaState(roomId);
            emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
        }
    }

    private void handleRemoveFromQueue(String socketId, String roomId, Map<String, Object> dataMap) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            int index = Integer.parseInt(String.valueOf(dataMap.get("index")));
            roomStateManager.removeFromQueue(roomId, index);
            persistMediaState(roomId);
            emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
        }
    }

    private void handleRemoveVideoHistory(String socketId, String roomId, Map<String, Object> dataMap) {
        if (!roomStateManager.isUserOwner(roomId, socketId)) return;
        String url = String.valueOf(dataMap.getOrDefault("url", ""));
        if (url.isBlank()) return;

        roomStateManager.removeVideoHistory(roomId, url);
        persistMediaState(roomId);
        emitVideoHistory(roomId);
    }

    private void handleClearVideoHistory(String socketId, String roomId) {
        if (!roomStateManager.isUserOwner(roomId, socketId)) return;
        roomStateManager.clearVideoHistory(roomId);
        persistMediaState(roomId);
        emitVideoHistory(roomId);
    }

    @SuppressWarnings("unchecked")
    private void handlePlayNext(String socketId, String roomId) {
        if (roomStateManager.isUserOwner(roomId, socketId)) {
            Object nextItem = roomStateManager.playNext(roomId);
            if (nextItem != null) {
                String newUrl = nextItem instanceof Map
                        ? String.valueOf(((Map<String, Object>) nextItem).get("url"))
                        : String.valueOf(nextItem);

                recordCurrentVideoInHistory(roomId, newUrl);
                roomStateManager.updateVideoUrl(roomId, newUrl);
                persistMediaState(roomId);
                emitToRoom(roomId, "video-url-change", Map.of("newUrl", newUrl));
                emitToRoom(roomId, "queue-updated", Map.of("queue", roomStateManager.getQueue(roomId)));
                emitVideoHistory(roomId);
            }
        }
    }

    private void recordCurrentVideoInHistory(String roomId, String nextUrl) {
        RoomStateManager.VideoState current = roomStateManager.getVideoState(roomId);
        if (current == null || current.getUrl() == null || current.getUrl().isBlank()
                || Objects.equals(current.getUrl(), nextUrl)) return;

        Map<String, Object> previous = new HashMap<>();
        previous.put("url", current.getUrl());
        previous.put("title", "YouTube Video");
        previous.put("thumbnail", youtubeThumbnail(current.getUrl()));
        roomStateManager.addVideoHistory(roomId, previous);
    }

    private String youtubeThumbnail(String url) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(url);
        return matcher.find() ? "https://img.youtube.com/vi/" + matcher.group(1) + "/mqdefault.jpg" : "";
    }

    private void emitVideoHistory(String roomId) {
        emitToRoom(roomId, "video-history-updated",
                Map.of("history", roomStateManager.getVideoHistorySnapshot(roomId)));
    }

    private void restoreMediaState(String roomId) {
        if (roomStateManager.hasMediaState(roomId)) return;
        roomMediaStateRepository.findById(roomId).ifPresent(saved -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        saved.getPayload(),
                        new TypeReference<Map<String, Object>>() {}
                );
                String url = payload.get("url") == null ? null : String.valueOf(payload.get("url"));
                boolean isPlaying = Boolean.TRUE.equals(payload.get("isPlaying"));
                double currentTime = payload.get("currentTime") instanceof Number number ? number.doubleValue() : 0;
                List<Object> queue = payload.get("queue") instanceof List<?> list
                        ? new ArrayList<>(list)
                        : Collections.emptyList();
                List<Map<String, Object>> history = new ArrayList<>();
                if (payload.get("history") instanceof List<?> list) {
                    for (Object item : list) {
                        Map<String, Object> video = asStringKeyedMap(item);
                        if (video != null) history.add(video);
                    }
                }
                roomStateManager.restoreMediaState(roomId, url, isPlaying, currentTime, queue, history);
            } catch (IOException ex) {
                log.error("Failed to restore saved media state for room {}", roomId, ex);
            }
        });
    }

    private void persistMediaState(String roomId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            RoomStateManager.VideoState videoState = roomStateManager.getVideoState(roomId);
            if (videoState != null) {
                payload.put("url", videoState.getUrl());
                payload.put("isPlaying", videoState.isPlaying());
                payload.put("currentTime", videoState.getCalculatedCurrentTime());
            }
            payload.put("queue", roomStateManager.getQueueSnapshot(roomId));
            payload.put("history", roomStateManager.getVideoHistorySnapshot(roomId));
            roomMediaStateRepository.save(new RoomMediaState(roomId, objectMapper.writeValueAsString(payload)));
        } catch (IOException ex) {
            log.error("Failed to serialize saved media state for room {}", roomId, ex);
        }
    }

    private void handleDrawLine(String socketId, String roomId, Map<String, Object> dataMap) {
        roomStateManager.addWhiteboardStroke(roomId, dataMap);
        broadcastToOthersInRoom(roomId, socketId, "draw-line", dataMap);
    }

    private void handleClearWhiteboard(String socketId, String roomId) {
        roomStateManager.clearWhiteboardStrokes(roomId);
        broadcastToOthersInRoom(roomId, socketId, "clear-whiteboard", Collections.emptyMap());
    }

    private void handleRequestWhiteboard(String socketId, String roomId) {
        List<Map<String, Object>> strokes = roomStateManager.getWhiteboardStrokes(roomId);
        if (strokes != null && !strokes.isEmpty()) {
            synchronized (strokes) {
                for (Map<String, Object> stroke : strokes) {
                    emitToSocket(socketId, "draw-line", stroke);
                }
            }
        }

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
            String name = String.valueOf(dataMap.get("name"));
            roomStateManager.setRoomName(roomId, name);
            emitToRoom(roomId, "room-name-changed", dataMap);
        }
    }

    private void handleSyncStateResponse(Map<String, Object> dataMap) {
        String requesterId = String.valueOf(dataMap.get("requesterId"));
        if (requesterId != null) {
            emitToSocket(requesterId, "sync-state-response", dataMap);
        }
    }

    private void handleChatMessage(String socketId, String roomId, Map<String, Object> dataMap) {
        Object msgObj = dataMap.get("message");
        if (msgObj == null) return;

        Map<String, Object> message = asStringKeyedMap(msgObj);
        if (message == null) return;

        Map<String, Object> roomUser = roomStateManager.getUser(roomId, socketId);
        if (roomUser == null) return;
        message.put("userId", String.valueOf(roomUser.getOrDefault("userId", "anonymous")));
        message.put("userName", String.valueOf(roomUser.getOrDefault("name", "Guest")));
        message.put("userImage", String.valueOf(roomUser.getOrDefault("image", "")));

        persistChatMessage(roomId, message);
        emitToRoom(roomId, "chat-message", message);

        String text = String.valueOf(message.getOrDefault("text", ""));
        if (aiAssistantService.isAiTrigger(text)) {
            emitToRoom(roomId, "typing", Map.of("userName", "SyncRoom AI 🤖"));

            scheduler.schedule(() -> {
                Map<String, Object> aiResponse = aiAssistantService.generateAiResponse(text);
                persistChatMessage(roomId, aiResponse);
                emitToRoom(roomId, "chat-message", aiResponse);
            }, 400, TimeUnit.MILLISECONDS);
        }
    }

    private void handleEditChatMessage(String socketId, String roomId, Map<String, Object> dataMap) {
        String messageId = String.valueOf(dataMap.getOrDefault("messageId", ""));
        String text = String.valueOf(dataMap.getOrDefault("text", "")).trim();
        Message savedMessage = findOwnedTextMessage(socketId, roomId, messageId);
        if (savedMessage == null) return;
        if (text.isBlank()) {
            emitChatOperationError(socketId, "A message cannot be empty.");
            return;
        }

        try {
            Map<String, Object> message = objectMapper.readValue(
                    savedMessage.getPayload(),
                    new TypeReference<Map<String, Object>>() {}
            );
            message.put("text", text);
            message.put("edited", true);
            savedMessage.setText(text);
            savedMessage.setPayload(writeChatPayload(message));
            messageRepository.save(savedMessage);
            roomStateManager.updateRoomMessage(roomId, message);
            emitToRoom(roomId, "chat-message-updated", message);
        } catch (IOException ex) {
            log.error("Failed to update chat message {} in room {}", messageId, roomId, ex);
            emitChatOperationError(socketId, "The message could not be updated. Please try again.");
        }
    }

    private void handleDeleteChatMessage(String socketId, String roomId, Map<String, Object> dataMap) {
        String messageId = String.valueOf(dataMap.getOrDefault("messageId", ""));
        Message savedMessage = findOwnedTextMessage(socketId, roomId, messageId);
        if (savedMessage == null) return;

        messageRepository.delete(savedMessage);
        roomStateManager.removeRoomMessage(roomId, messageId);
        emitToRoom(roomId, "chat-message-deleted", Map.of("messageId", messageId));
    }

    private Message findOwnedTextMessage(String socketId, String roomId, String messageId) {
        Map<String, Object> roomUser = roomStateManager.getUser(roomId, socketId);
        if (roomUser == null || messageId.isBlank()) return null;

        String userId = String.valueOf(roomUser.getOrDefault("userId", "anonymous"));
        Optional<Message> saved = messageRepository.findFirstByRoomIdAndClientMessageId(roomId, messageId);
        if (saved.isEmpty()) {
            for (Message candidate : messageRepository.findTop500ByRoomIdAndUserIdOrderByCreatedAtDesc(roomId, userId)) {
                if (candidate.getPayload() == null) continue;
                try {
                    Map<String, Object> payload = objectMapper.readValue(
                            candidate.getPayload(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    if (Objects.equals(String.valueOf(payload.get("id")), messageId)) {
                        candidate.setClientMessageId(messageId);
                        saved = Optional.of(messageRepository.save(candidate));
                        break;
                    }
                } catch (IOException ex) {
                    log.error("Failed to inspect chat message {} in room {}", candidate.getId(), roomId, ex);
                }
            }
        }
        if (saved.isEmpty() || !Objects.equals(saved.get().getUserId(), userId)) {
            emitChatOperationError(socketId, "You can only change your own text messages.");
            return null;
        }
        if (saved.get().getPayload() == null) {
            emitChatOperationError(socketId, "This message cannot be changed.");
            return null;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    saved.get().getPayload(),
                    new TypeReference<Map<String, Object>>() {}
            );
            if ("file".equals(payload.get("type")) || Boolean.TRUE.equals(payload.get("isAi"))) return null;
        } catch (IOException | IllegalArgumentException ex) {
            log.error("Failed to verify chat message {} in room {}", messageId, roomId, ex);
            emitChatOperationError(socketId, "The message could not be verified.");
            return null;
        }
        return saved.get();
    }

    private void emitChatOperationError(String socketId, String message) {
        emitToSocket(socketId, "chat-operation-error", Map.of("message", message));
    }

    private List<Map<String, Object>> loadChatHistory(String roomId) {
        List<Message> savedMessages = messageRepository.findTop500ByRoomIdOrderByCreatedAtDesc(roomId);
        if (savedMessages.isEmpty()) {
            return roomStateManager.getRoomMessages(roomId);
        }
        List<Map<String, Object>> history = new ArrayList<>(savedMessages.size());
        for (Message savedMessage : savedMessages) {
            try {
                Map<String, Object> message = savedMessage.getPayload() == null
                        ? new HashMap<>()
                        : objectMapper.readValue(
                                savedMessage.getPayload(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                message.putIfAbsent("id", savedMessage.getId());
                message.putIfAbsent("userId", savedMessage.getUserId());
                message.putIfAbsent("userName", savedMessage.getUserName());
                message.putIfAbsent("userImage", savedMessage.getUserImage());
                message.putIfAbsent("text", savedMessage.getText());
                message.putIfAbsent("timestamp", savedMessage.getCreatedAt() == null
                        ? Instant.now().toString()
                        : savedMessage.getCreatedAt().toString());
                history.add(message);
            } catch (IOException ex) {
                log.error("Failed to restore chat message {} for room {}", savedMessage.getId(), roomId, ex);
            }
        }
        Collections.reverse(history);
        return history;
    }

    private void persistChatMessage(String roomId, Map<String, Object> messageData) {
        String text = String.valueOf(messageData.getOrDefault("text", ""));
        if (text.isBlank()) {
            log.warn("Not saving an empty chat message for room {}", roomId);
            return;
        }

        Message message = new Message(
                roomId,
                String.valueOf(messageData.getOrDefault("userId", "unknown")),
                text,
                String.valueOf(messageData.getOrDefault("userName", "Guest")),
                String.valueOf(messageData.getOrDefault("userImage", ""))
        );
        message.setPayload(writeChatPayload(messageData));
        message.setClientMessageId(String.valueOf(messageData.getOrDefault("id", "")));
        message.setCreatedAt(readMessageTimestamp(messageData.get("timestamp")));
        messageRepository.save(message);
        roomStateManager.addRoomMessage(roomId, messageData);
    }

    private String writeChatPayload(Map<String, Object> messageData) {
        try {
            return objectMapper.writeValueAsString(messageData);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save chat message payload", ex);
        }
    }

    private Instant readMessageTimestamp(Object timestamp) {
        if (timestamp instanceof String value) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ex) {
                log.warn("Invalid chat timestamp '{}'; using server time", value);
            }
        }
        return Instant.now();
    }

    private Map<String, Object> asStringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return null;
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, item) -> {
            if (key instanceof String stringKey) result.put(stringKey, item);
        });
        return result;
    }

    private void handlePinMessage(String roomId, Map<String, Object> dataMap) {
        Map<String, Object> message = asStringKeyedMap(dataMap.get("message"));
        if (message == null || message.get("id") == null) return;
        String noteId = String.valueOf(message.get("id"));
        pinnedNoteRepository.deleteByRoomIdAndNoteId(roomId, noteId);
        pinnedNoteRepository.save(new PinnedNote(
                roomId,
                noteId,
                writeChatPayload(message),
                Instant.now()
        ));
        roomStateManager.addPinnedNote(roomId, message);

        emitToRoom(roomId, "pinned-notes-updated",
                Map.of("notes", loadPinnedNotes(roomId)));
    }

    private void handleUnpinMessage(String roomId, Map<String, Object> dataMap) {
        String noteId = String.valueOf(dataMap.get("noteId"));

        if (noteId == null || "null".equals(noteId)) {
            return;
        }

        pinnedNoteRepository.deleteByRoomIdAndNoteId(roomId, noteId);
        roomStateManager.removePinnedNote(roomId, noteId);

        emitToRoom(roomId, "pinned-notes-updated",
                Map.of("notes", loadPinnedNotes(roomId)));
    }

    private void handleRequestPinnedNotes(String socketId, String roomId) {
        emitToSocket(socketId, "pinned-notes-updated",
                Map.of("notes", loadPinnedNotes(roomId)));
    }

    private List<Map<String, Object>> loadPinnedNotes(String roomId) {
        List<Map<String, Object>> notes = new ArrayList<>();
        for (PinnedNote pinnedNote : pinnedNoteRepository.findByRoomIdOrderByPinnedAtDesc(roomId)) {
            try {
                Map<String, Object> note = objectMapper.readValue(
                        pinnedNote.getPayload(),
                        new TypeReference<Map<String, Object>>() {}
                );
                notes.add(note);
            } catch (IOException ex) {
                log.error("Failed to restore pinned note {} for room {}", pinnedNote.getNoteId(), roomId, ex);
            }
        }
        return notes;
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
            boolean isPrimary = roomStateManager.isUserPrimaryOwner(roomId, targetSocketId);
            emitToRoom(roomId, "owner-status-update", Map.of(
                    "socketId", targetSocketId,
                    "isOwner", true,
                    "isPrimaryOwner", isPrimary
            ));
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
            boolean isPrimary = roomStateManager.isUserPrimaryOwner(roomId, targetSocketId);
            emitToRoom(roomId, "owner-status-update", Map.of(
                    "socketId", targetSocketId,
                    "isOwner", true,
                    "isPrimaryOwner", isPrimary
            ));
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

    private void handleFileShare(String socketId, String roomId, Map<String, Object> dataMap) {
        Map<String, Object> fileInfo = new HashMap<>(dataMap);
        fileInfo.put("senderId", socketId);
        fileInfo.put("timestamp", System.currentTimeMillis());
        roomStateManager.addRoomFile(roomId, fileInfo);

        Map<String, Object> chatFile = new HashMap<>();
        chatFile.put("id", fileInfo.getOrDefault("id", UUID.randomUUID().toString()));
        chatFile.put("userId", socketId);
        chatFile.put("userName", fileInfo.getOrDefault("uploaderName", "Guest"));
        chatFile.put("userImage", fileInfo.getOrDefault("uploaderImage", ""));
        chatFile.put("text", fileInfo.getOrDefault("originalFilename", "Shared file"));
        chatFile.put("timestamp", Instant.now().toString());
        chatFile.put("type", "file");
        chatFile.put("fileName", fileInfo.getOrDefault("originalFilename", "Shared file"));
        chatFile.put("fileSize", fileInfo.get("fileSize"));
        chatFile.put("fileUrl", fileInfo.get("downloadUrl"));
        chatFile.put("fileType", fileInfo.get("contentType"));
        persistChatMessage(roomId, chatFile);

        emitToRoom(roomId, "file-shared", fileInfo);
    }

    private void handleRequestFiles(String socketId, String roomId) {
        List<Map<String, Object>> files = roomStateManager.getRoomFiles(roomId);
        emitToSocket(socketId, "files-list", Map.of("files", files));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String socketId = session.getId();
        sessions.remove(socketId);
        socketToRoom.remove(socketId);

        RoomStateManager.DisconnectResult result = roomStateManager.removeUserFromAllRooms(socketId);
        if (result != null) {
            String roomId = result.getRoomId();
            persistMediaState(roomId);
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
