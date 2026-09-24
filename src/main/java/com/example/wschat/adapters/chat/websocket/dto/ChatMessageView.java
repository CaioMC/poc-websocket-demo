package com.example.wschat.adapters.chat.websocket.dto;

import java.time.Instant;

import com.example.wschat.core.chat.domain.ChatMessage;

/** Representação serializável (JSON) de uma {@link ChatMessage} para o cliente WebSocket. */
public record ChatMessageView(String id, String role, String content, Instant createdAt, boolean interrupted) {

    public static ChatMessageView from(ChatMessage message) {
        return new ChatMessageView(
            message.id(),
            message.role().name(),
            message.content(),
            message.createdAt(),
            message.interrupted()
        );
    }
}
