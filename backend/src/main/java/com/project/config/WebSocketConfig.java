package com.project.config;

import com.project.websocket.PeerJsWebSocketHandler;
import com.project.websocket.SocketIoWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

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
                .addInterceptors(proxyHandshakeInterceptor());

        registry.addHandler(peerJsWebSocketHandler, "/peerjs/myapp", "/peerjs/**")
                .setAllowedOriginPatterns("*")
                .addInterceptors(proxyHandshakeInterceptor());
    }

    /**
     * Interceptor to handle proxy headers (X-Forwarded-Proto, X-Forwarded-For)
     * for WebSocket upgrade requests behind Render's reverse proxy.
     * The server.forward-headers-strategy=native in application.properties
     * handles most proxy header processing automatically.
     */
    private HandshakeInterceptor proxyHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {
                // Handle X-Forwarded-Proto header from Render proxy
                String forwardedProto = request.getHeaders().getFirst("X-Forwarded-Proto");
                if ("https".equals(forwardedProto)) {
                    attributes.put("org.springframework.web.socket.server.support.RemoteIpValve", "https");
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Exception ex) {
                // No-op
            }
        };
    }
}
