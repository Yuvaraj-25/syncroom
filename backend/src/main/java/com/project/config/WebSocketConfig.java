package com.project.config;

import com.project.websocket.PeerJsWebSocketHandler;
import com.project.websocket.SocketIoWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
                .setAllowedOriginPatterns("*");

        registry.addHandler(peerJsWebSocketHandler, "/peerjs/myapp", "/peerjs/**")
                .setAllowedOriginPatterns("*");
    }
}
