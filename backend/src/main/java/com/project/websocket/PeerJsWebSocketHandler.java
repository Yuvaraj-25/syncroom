package com.project.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PeerJsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PeerJsWebSocketHandler.class);

    // peerId -> WebSocketSession
    private final Map<String, WebSocketSession> peers = new ConcurrentHashMap<>();
    // sessionId -> peerId
    private final Map<String, String> sessionToPeerId = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public PeerJsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String peerId = extractQueryParam(uri, "id");

        if (peerId != null && !peerId.isBlank()) {
            // Check if peerId already exists (reconnection)
            WebSocketSession existingSession = peers.get(peerId);
            if (existingSession != null && existingSession.isOpen()) {
                log.warn("PeerJS duplicate connection for peerId={}, closing old session", peerId);
                try {
                    existingSession.close(CloseStatus.NORMAL);
                } catch (Exception e) {
                    log.debug("Error closing old session for peerId={}", peerId, e);
                }
            }

            peers.put(peerId, session);
            sessionToPeerId.put(session.getId(), peerId);
            log.info("PeerJS client connected: peerId={}, sessionId={}", peerId, session.getId());

            // PeerJS handshake OPEN response
            String openMsg = "{\"type\":\"OPEN\"}";
            session.sendMessage(new TextMessage(openMsg));
        } else {
            log.warn("PeerJS connection attempt without 'id' parameter: uri={}", uri);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");

            // Handle PeerJS protocol message types
            switch (type.toUpperCase()) {
                case "HEARTBEAT", "PING" -> {
                    return;
                }
                case "OFFER", "ANSWER", "CANDIDATE", "LEAVE" -> {
                    // These are signaling messages that should be forwarded to the target peer
                    String dst = root.path("dst").asText("");
                    if (!dst.isBlank()) {
                        forwardToPeer(dst, sessionToPeerId.get(session.getId()), root);
                    } else {
                        log.warn("PeerJS {} message missing 'dst' field: {}", type, payload);
                    }
                    return;
                }
                default -> {
                    // Unknown message type, try to forward if it has dst
                    String dst = root.path("dst").asText("");
                    if (!dst.isBlank()) {
                        forwardToPeer(dst, sessionToPeerId.get(session.getId()), root);
                    } else {
                        log.debug("PeerJS unknown message type '{}' without dst: {}", type, payload);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error handling PeerJS message: payload={}", payload, ex);
        }
    }

    private void forwardToPeer(String dst, String sourcePeerId, JsonNode payload) {
        if (sourcePeerId == null || !payload.isObject()) {
            log.warn("Cannot forward PeerJS message without a registered source peer: dst={}", dst);
            return;
        }
        WebSocketSession targetSession = peers.get(dst);
        if (targetSession != null && targetSession.isOpen()) {
            synchronized (targetSession) {
                try {
                    ObjectNode forwardedMessage = ((ObjectNode) payload).deepCopy();
                    forwardedMessage.put("src", sourcePeerId);
                    targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(forwardedMessage)));
                    log.debug("PeerJS forwarded message to dst={}", dst);
                } catch (Exception e) {
                    log.error("Failed to forward message to dst={}", dst, e);
                }
            }
        } else {
            log.debug("PeerJS target not found or closed: dst={}", dst);
            // Optionally notify sender that target is unavailable
            // Could send back an ERROR message to the sender
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String peerId = sessionToPeerId.remove(session.getId());
        if (peerId != null) {
            peers.remove(peerId, session);
            log.info("PeerJS client disconnected: peerId={}, status={}", peerId, status);

            // Notify other peers that this peer has left (optional)
            // Could broadcast a LEAVE message to all connected peers
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("PeerJS transport error: session={}", session.getId(), exception);
    }

    private String extractQueryParam(URI uri, String paramName) {
        if (uri == null || uri.getQuery() == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1 && paramName.equalsIgnoreCase(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
