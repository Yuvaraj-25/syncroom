package com.project.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            peers.put(peerId, session);
            sessionToPeerId.put(session.getId(), peerId);
            log.info("PeerJS client connected: peerId={}, sessionId={}", peerId, session.getId());

            // PeerJS handshake OPEN response
            String openMsg = "{\"type\":\"OPEN\"}";
            session.sendMessage(new TextMessage(openMsg));
        } else {
            log.warn("PeerJS connection attempt without 'id' parameter: uri={}", uri);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) return;

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");

            if ("HEARTBEAT".equalsIgnoreCase(type) || "PING".equalsIgnoreCase(type)) {
                // Heartbeat / Ping response to prevent client timeouts
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                return;
            }

            String dst = root.path("dst").asText("");
            if (!dst.isBlank()) {
                WebSocketSession targetSession = peers.get(dst);
                if (targetSession != null && targetSession.isOpen()) {
                    synchronized (targetSession) {
                        targetSession.sendMessage(new TextMessage(payload));
                    }
                } else {
                    log.debug("PeerJS target not found or closed: dst={}", dst);
                }
            }
        } catch (Exception ex) {
            log.error("Error handling PeerJS message: payload={}", payload, ex);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String peerId = sessionToPeerId.remove(session.getId());
        if (peerId != null) {
            peers.remove(peerId);
            log.info("PeerJS client disconnected: peerId={}, status={}", peerId, status);
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
