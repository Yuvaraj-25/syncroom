package com.project.config;

import com.project.websocket.PeerJsWebSocketHandler;
import com.project.websocket.SocketIoWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SocketIoWebSocketHandler socketIoWebSocketHandler;
    private final PeerJsWebSocketHandler peerJsWebSocketHandler;

    public WebSocketConfig(SocketIoWebSocketHandler socketIoWebSocketHandler,
                           PeerJsWebSocketHandler peerJsWebSocketHandler) {
        this.socketIoWebSocketHandler = socketIoWebSocketHandler;
        this.peerJsWebSocketHandler = peerJsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socketIoWebSocketHandler, "/socket.io/**", "/socket.io/")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(proxyHandshakeHandler());

        registry.addHandler(peerJsWebSocketHandler, "/peerjs/myapp", "/peerjs/**")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(proxyHandshakeHandler());
    }

    @Bean
    public HandshakeHandler proxyHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected boolean upgradeInternal(ServerHttpRequest request, ServerHttpResponse response,
                                              String selectedProtocol, WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                // Handle proxy headers for WebSocket upgrade
                // Render's proxy may strip Upgrade header, but the request comes through
                // We need to ensure the upgrade can proceed
                
                // Copy X-Forwarded-Proto to attributes for downstream use
                String forwardedProto = request.getHeaders().getFirst("X-Forwarded-Proto");
                if ("https".equals(forwardedProto)) {
                    attributes.put("org.springframework.web.socket.server.support.RemoteIpValve", "https");
                }
                
                // Ensure Origin header is present (Render may modify it)
                String origin = request.getHeaders().getOrigin();
                if (origin != null) {
                    attributes.put("origin", origin);
                }
                
                return super.upgradeInternal(request, response, selectedProtocol, wsHandler, attributes);
            }

            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                // Allow unauthenticated WebSocket connections
                // JWT auth happens after connection via Socket.IO/Engine.IO protocol
                return () -> "anonymous";
            }
        };
    }
}
