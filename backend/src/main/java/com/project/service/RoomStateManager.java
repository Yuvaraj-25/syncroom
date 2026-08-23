package com.project.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomStateManager {

    // roomId -> Map<socketId, Map<String, Object>>
    private final Map<String, Map<String, Map<String, Object>>> rooms = new ConcurrentHashMap<>();
    
    // roomId -> List<Object>
    private final Map<String, List<Object>> roomQueues = new ConcurrentHashMap<>();
    
    // roomId -> List<Map<String, Object>>
    private final Map<String, List<Map<String, Object>>> roomPolls = new ConcurrentHashMap<>();
    
    // roomId -> List<Map<String, Object>>
    private final Map<String, List<Map<String, Object>>> roomQA = new ConcurrentHashMap<>();

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
                    roomQueues.remove(roomId);
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
        polls.add(poll);
    }

    @SuppressWarnings("unchecked")
    public void votePoll(String roomId, String pollId, Object optionIndex, String userId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            for (Map<String, Object> poll : polls) {
                if (Objects.equals(poll.get("id"), pollId)) {
                    if (!Boolean.TRUE.equals(poll.get("isOpen"))) return;

                    Map<String, List<String>> votes = (Map<String, List<String>>) poll.get("votes");
                    if (votes == null) {
                        votes = new HashMap<>();
                        poll.put("votes", votes);
                    }

                    // Remove existing vote
                    for (List<String> list : votes.values()) {
                        if (list != null) {
                            list.remove(userId);
                        }
                    }

                    // Add vote to optionIndex
                    String optKey = String.valueOf(optionIndex);
                    votes.putIfAbsent(optKey, new ArrayList<>());
                    votes.get(optKey).add(userId);
                    break;
                }
            }
        }
    }

    public void closePoll(String roomId, String pollId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            for (Map<String, Object> poll : polls) {
                if (Objects.equals(poll.get("id"), pollId)) {
                    poll.put("isOpen", false);
                    break;
                }
            }
        }
    }

    public void deletePoll(String roomId, String pollId) {
        List<Map<String, Object>> polls = getPolls(roomId);
        synchronized (polls) {
            polls.removeIf(p -> Objects.equals(p.get("id"), pollId));
        }
    }

    // ── Q&A ───────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getQuestions(String roomId) {
        return roomQA.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public void submitQuestion(String roomId, Map<String, Object> question) {
        List<Map<String, Object>> list = getQuestions(roomId);
        list.add(question);
    }

    @SuppressWarnings("unchecked")
    public void upvoteQuestion(String roomId, String questionId, String userId) {
        List<Map<String, Object>> list = getQuestions(roomId);
        synchronized (list) {
            for (Map<String, Object> q : list) {
                if (Objects.equals(q.get("id"), questionId)) {
                    List<String> upvotes = (List<String>) q.get("upvotes");
                    if (upvotes == null) {
                        upvotes = new ArrayList<>();
                        q.put("upvotes", upvotes);
                    }
                    if (upvotes.contains(userId)) {
                        upvotes.remove(userId);
                    } else {
                        upvotes.add(userId);
                    }
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
                if (Objects.equals(q.get("id"), questionId)) {
                    List<Object> answers = (List<Object>) q.get("answers");
                    if (answers == null) {
                        answers = new ArrayList<>();
                        q.put("answers", answers);
                    }
                    answers.add(answer);
                    break;
                }
            }
        }
    }

    public void deleteQuestion(String roomId, String questionId) {
        List<Map<String, Object>> list = getQuestions(roomId);
        synchronized (list) {
            list.removeIf(q -> Objects.equals(q.get("id"), questionId));
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
