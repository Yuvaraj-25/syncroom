package com.project.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomStateManager {

    // roomId -> Map<socketId, Map<String, Object>>
    private final Map<String, Map<String, Map<String, Object>>> rooms = new ConcurrentHashMap<>();

    // roomId -> custom room name
    private final Map<String, String> roomNames = new ConcurrentHashMap<>();

    // roomId -> VideoState
    private final Map<String, VideoState> roomVideoStates = new ConcurrentHashMap<>();

    // roomId -> List<Object>
    private final Map<String, List<Object>> roomQueues = new ConcurrentHashMap<>();

    // roomId -> List<Map<String, Object>>
    private final Map<String, List<Map<String, Object>>> roomWhiteboardStrokes = new ConcurrentHashMap<>();

    // roomId -> List<Map<String, Object>>
    private final Map<String, List<Map<String, Object>>> roomPolls = new ConcurrentHashMap<>();

    // roomId -> List<Map<String, Object>>
    private final Map<String, List<Map<String, Object>>> roomQA = new ConcurrentHashMap<>();

    // roomId -> List<Map<String, Object>> (shared files)
    private final Map<String, List<Map<String, Object>>> roomFiles = new ConcurrentHashMap<>();

    // ── Room Name ─────────────────────────────────────────────────────────────

    public void setRoomName(String roomId, String name) {
        if (name != null && !name.isBlank()) {
            roomNames.put(roomId, name.trim());
        }
    }

    public String getRoomName(String roomId) {
        return roomNames.get(roomId);
    }

    // ── Video Playback State ──────────────────────────────────────────────────

    public static class VideoState {
        private String url;
        private boolean isPlaying;
        private double currentTime;
        private long lastStateUpdate;

        public VideoState(String url, boolean isPlaying, double currentTime) {
            this.url = url;
            this.isPlaying = isPlaying;
            this.currentTime = Math.max(0, currentTime);
            this.lastStateUpdate = System.currentTimeMillis();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
            this.currentTime = 0;
            this.lastStateUpdate = System.currentTimeMillis();
        }

        public boolean isPlaying() {
            return isPlaying;
        }

        public void setPlaying(boolean playing) {
            this.isPlaying = playing;
            this.lastStateUpdate = System.currentTimeMillis();
        }

        public double getCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(double currentTime) {
            this.currentTime = Math.max(0, currentTime);
            this.lastStateUpdate = System.currentTimeMillis();
        }

        public double getCalculatedCurrentTime() {
            if (!isPlaying) {
                return currentTime;
            }
            double elapsedSeconds = (System.currentTimeMillis() - lastStateUpdate) / 1000.0;
            return currentTime + elapsedSeconds;
        }
    }

    public VideoState getVideoState(String roomId) {
        return roomVideoStates.get(roomId);
    }

    public void updateVideoUrl(String roomId, String url) {
        if (url != null && !url.isBlank()) {
            VideoState state = roomVideoStates.computeIfAbsent(roomId, k -> new VideoState(url, true, 0));
            state.setUrl(url);
            state.setPlaying(true);
        }
    }

    public void updateVideoPlay(String roomId, Double time) {
        VideoState state = roomVideoStates.computeIfAbsent(roomId, k -> new VideoState("aqz-KE-bpKQ", true, 0));
        if (time != null && time >= 0) {
            state.setCurrentTime(time);
        }
        state.setPlaying(true);
    }

    public void updateVideoPause(String roomId, Double time) {
        VideoState state = roomVideoStates.computeIfAbsent(roomId, k -> new VideoState("aqz-KE-bpKQ", false, 0));
        if (time != null && time >= 0) {
            state.setCurrentTime(time);
        }
        state.setPlaying(false);
    }

    public void updateVideoSeek(String roomId, double time) {
        VideoState state = roomVideoStates.computeIfAbsent(roomId, k -> new VideoState("aqz-KE-bpKQ", false, 0));
        state.setCurrentTime(time);
    }

    // ── Whiteboard Strokes ────────────────────────────────────────────────────

    public List<Map<String, Object>> getWhiteboardStrokes(String roomId) {
        return roomWhiteboardStrokes.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void addWhiteboardStroke(String roomId, Map<String, Object> stroke) {
        List<Map<String, Object>> strokes = getWhiteboardStrokes(roomId);
        synchronized (strokes) {
            // Cap history at 5,000 strokes to prevent unbounded memory growth
            if (strokes.size() > 5000) {
                strokes.subList(0, 1000).clear();
            }
            strokes.add(new HashMap<>(stroke));
        }
    }

    public void clearWhiteboardStrokes(String roomId) {
        List<Map<String, Object>> strokes = roomWhiteboardStrokes.get(roomId);
        if (strokes != null) {
            synchronized (strokes) {
                strokes.clear();
            }
        }
    }

    // ── Room Users ───────────────────────────────────────────────────────────

    public synchronized Map<String, Object> joinRoom(String roomId, String socketId, Map<String, Object> rawUser) {
        rooms.putIfAbsent(roomId, new ConcurrentHashMap<>());
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);

        boolean isFirstUser = roomUsers.isEmpty();
        Map<String, Object> enhancedUser = new ConcurrentHashMap<>();
        if (rawUser != null) {
            enhancedUser.putAll(rawUser);
        }
        enhancedUser.put("isOwner", isFirstUser);
        enhancedUser.put("isPrimaryOwner", isFirstUser);
        enhancedUser.put("isHandRaised", false);
        enhancedUser.putIfAbsent("isMuted", true);
        enhancedUser.putIfAbsent("isVideoOn", false);

        roomUsers.put(socketId, enhancedUser);
        return enhancedUser;
    }

    public List<Map<String, Object>> getUsersInRoom(String roomId) {
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);
        if (roomUsers == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : roomUsers.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("socketId", entry.getKey());
            item.put("user", entry.getValue());
            list.add(item);
        }
        return list;
    }

    public Map<String, Object> getUser(String roomId, String socketId) {
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);
        if (roomUsers == null) return null;
        return roomUsers.get(socketId);
    }

    public boolean isUserOwner(String roomId, String socketId) {
        Map<String, Object> user = getUser(roomId, socketId);
        return user != null && Boolean.TRUE.equals(user.get("isOwner"));
    }

    public boolean isUserPrimaryOwner(String roomId, String socketId) {
        Map<String, Object> user = getUser(roomId, socketId);
        return user != null && Boolean.TRUE.equals(user.get("isPrimaryOwner"));
    }

    public String getPrimaryOwnerSocketId(String roomId) {
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);
        if (roomUsers == null) return null;
        for (Map.Entry<String, Map<String, Object>> entry : roomUsers.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().get("isPrimaryOwner"))) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void updateUserField(String roomId, String socketId, String field, Object value) {
        Map<String, Object> user = getUser(roomId, socketId);
        if (user != null) {
            user.put(field, value);
        }
    }

    public void grantOwner(String roomId, String socketId) {
        updateUserField(roomId, socketId, "isOwner", true);
    }

    public void lowerAllHands(String roomId) {
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);
        if (roomUsers != null) {
            for (Map<String, Object> user : roomUsers.values()) {
                user.put("isHandRaised", false);
            }
        }
    }

    public Set<String> getSocketIdsInRoom(String roomId) {
        Map<String, Map<String, Object>> roomUsers = rooms.get(roomId);
        if (roomUsers == null) return Collections.emptySet();
        return new HashSet<>(roomUsers.keySet());
    }

    public synchronized DisconnectResult removeUserFromAllRooms(String socketId) {
        DisconnectResult result = null;
        for (Iterator<Map.Entry<String, Map<String, Map<String, Object>>>> it = rooms.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Map<String, Map<String, Object>>> entry = it.next();
            String roomId = entry.getKey();
            Map<String, Map<String, Object>> roomUsers = entry.getValue();

            if (roomUsers.containsKey(socketId)) {
                Map<String, Object> removedUser = roomUsers.remove(socketId);
                boolean wasPrimary = removedUser != null && Boolean.TRUE.equals(removedUser.get("isPrimaryOwner"));

                String newPrimarySocketId = null;
                if (roomUsers.isEmpty()) {
                    it.remove();
                    roomNames.remove(roomId);
                    roomVideoStates.remove(roomId);
                    roomQueues.remove(roomId);
                    roomWhiteboardStrokes.remove(roomId);
                    roomPolls.remove(roomId);
                    roomQA.remove(roomId);
                } else if (wasPrimary) {
                    newPrimarySocketId = roomUsers.keySet().iterator().next();
                    Map<String, Object> newPrimary = roomUsers.get(newPrimarySocketId);
                    newPrimary.put("isOwner", true);
                    newPrimary.put("isPrimaryOwner", true);
                }

                result = new DisconnectResult(roomId, removedUser, newPrimarySocketId);
                break;
            }
        }
        return result;
    }

    // ── Video Queues ──────────────────────────────────────────────────────────

    public List<Object> getQueue(String roomId) {
        return roomQueues.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void addToQueue(String roomId, Object item) {
        List<Object> queue = getQueue(roomId);
        queue.add(item);
    }

    public void removeFromQueue(String roomId, int index) {
        List<Object> queue = getQueue(roomId);
        synchronized (queue) {
            if (index >= 0 && index < queue.size()) {
                queue.remove(index);
            }
        }
    }

    public Object playNext(String roomId) {
        List<Object> queue = getQueue(roomId);
        synchronized (queue) {
            if (!queue.isEmpty()) {
                return queue.remove(0);
            }
        }
        return null;
    }

    // ── Polls ─────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getPolls(String roomId) {
        return roomPolls.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void createPoll(String roomId, Map<String, Object> poll) {
        List<Map<String, Object>> polls = getPolls(roomId);
        Map<String, Object> safePoll = new HashMap<>(poll);
        safePoll.putIfAbsent("isOpen", true);
        polls.add(safePoll);
    }

    @SuppressWarnings("unchecked")
    public void votePoll(String roomId, String pollId, Object optionIndex, String userId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            for (Map<String, Object> poll : polls) {
                if (Objects.equals(String.valueOf(poll.get("id")), String.valueOf(pollId))) {
                    Object isOpenVal = poll.get("isOpen");
                    boolean isOpen = Boolean.TRUE.equals(isOpenVal) || "true".equalsIgnoreCase(String.valueOf(isOpenVal));
                    if (!isOpen) return;

                    Map<String, List<String>> votes = (Map<String, List<String>>) poll.get("votes");
                    if (votes == null) {
                        votes = new HashMap<>();
                        poll.put("votes", votes);
                    }

                    // Remove existing vote across all options
                    for (Map.Entry<String, List<String>> entry : votes.entrySet()) {
                        if (entry.getValue() != null) {
                            List<String> mutableList = new ArrayList<>(entry.getValue());
                            mutableList.remove(userId);
                            entry.setValue(mutableList);
                        }
                    }

                    // Add vote to optionIndex
                    String optKey = String.valueOf(optionIndex);
                    List<String> optionVotes = votes.get(optKey);
                    if (optionVotes == null) {
                        optionVotes = new ArrayList<>();
                    } else {
                        optionVotes = new ArrayList<>(optionVotes);
                    }
                    if (!optionVotes.contains(userId)) {
                        optionVotes.add(userId);
                    }
                    votes.put(optKey, optionVotes);
                    break;
                }
            }
        }
    }

    public void closePoll(String roomId, String pollId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            for (Map<String, Object> poll : polls) {
                if (Objects.equals(String.valueOf(poll.get("id")), String.valueOf(pollId))) {
                    poll.put("isOpen", false);
                    break;
                }
            }
        }
    }

    public void deletePoll(String roomId, String pollId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            polls.removeIf(p -> Objects.equals(String.valueOf(p.get("id")), String.valueOf(pollId)));
        }
    }

    // ── Q&A ───────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getQuestions(String roomId) {
        return roomQA.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void submitQuestion(String roomId, Map<String, Object> question) {
        List<Map<String, Object>> list = getQuestions(roomId);
        Map<String, Object> safeQuestion = new HashMap<>(question);
        safeQuestion.putIfAbsent("upvotes", new ArrayList<String>());
        safeQuestion.putIfAbsent("answers", new ArrayList<Object>());
        list.add(safeQuestion);
    }

    @SuppressWarnings("unchecked")
    public void upvoteQuestion(String roomId, String questionId, String userId) {
        List<Map<String, Object>> list = getQuestions(roomId);
        synchronized (list) {
            for (Map<String, Object> q : list) {
                if (Objects.equals(String.valueOf(q.get("id")), String.valueOf(questionId))) {
                    List<String> upvotes = (List<String>) q.get("upvotes");
                    List<String> mutableUpvotes = upvotes != null ? new ArrayList<>(upvotes) : new ArrayList<>();
                    if (mutableUpvotes.contains(userId)) {
                        mutableUpvotes.remove(userId);
                    } else {
                        mutableUpvotes.add(userId);
                    }
                    q.put("upvotes", mutableUpvotes);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void answerQuestion(String roomId, String questionId, Object answer) {
        List<Map<String, Object>> list = getQuestions(roomId);
        synchronized (list) {
            for (Map<String, Object> q : list) {
                if (Objects.equals(String.valueOf(q.get("id")), String.valueOf(questionId))) {
                    List<Object> answers = (List<Object>) q.get("answers");
                    List<Object> mutableAnswers = answers != null ? new ArrayList<>(answers) : new ArrayList<>();
                    mutableAnswers.add(answer);
                    q.put("answers", mutableAnswers);
                    break;
                }
            }
        }
    }

    public void deleteQuestion(String roomId, String questionId) {
        List<Map<String, Object>> list = getQuestions(roomId);
        synchronized (list) {
            list.removeIf(q -> Objects.equals(String.valueOf(q.get("id")), String.valueOf(questionId)));
        }
    }

    // ── Shared Files ────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getRoomFiles(String roomId) {
        return roomFiles.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void addRoomFile(String roomId, Map<String, Object> fileInfo) {
        List<Map<String, Object>> files = getRoomFiles(roomId);
        synchronized (files) {
            files.add(0, new HashMap<>(fileInfo)); // Add to beginning for newest first
            // Cap at 500 files per room
            if (files.size() > 500) {
                files.subList(500, files.size()).clear();
            }
        }
    }

    public static class DisconnectResult {
        private final String roomId;
        private final Map<String, Object> user;
        private final String newPrimarySocketId;

        public DisconnectResult(String roomId, Map<String, Object> user, String newPrimarySocketId) {
            this.roomId = roomId;
            this.user = user;
            this.newPrimarySocketId = newPrimarySocketId;
        }

        public String getRoomId() {
            return roomId;
        }

        public Map<String, Object> getUser() {
            return user;
        }

        public String getNewPrimarySocketId() {
            return newPrimarySocketId;
        }
    }
}
