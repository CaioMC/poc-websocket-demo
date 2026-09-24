package com.example.wschat.core.chat.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma mensagem imutável do histórico de um {@link Chat}.
 *
 * @param interrupted marca uma resposta do assistente truncada por interrupção do usuário.
 */
public record ChatMessage(String id, ChatRole role, String content, Instant createdAt, boolean interrupted) {

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role não pode ser nulo");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content não pode ser nulo ou vazio");
        }
    }

    public static ChatMessage of(ChatRole role, String content) {
        return new ChatMessage(UUID.randomUUID().toString(), role, content, Instant.now(), false);
    }

    public static ChatMessage interrupted(ChatRole role, String partialContent) {
        return new ChatMessage(UUID.randomUUID().toString(), role, partialContent, Instant.now(), true);
    }
}
