package com.example.wschat.adapters.chat.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registra o {@link ChatWebSocketHandler} no endpoint {@code /ws/chat}. */
@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final String[] allowedOriginPatterns;

    public ChatWebSocketConfig(
        ChatWebSocketHandler chatWebSocketHandler,
        @Value("${app.websocket.allowed-origin-patterns:*}") String[] allowedOriginPatterns
    ) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
            .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
