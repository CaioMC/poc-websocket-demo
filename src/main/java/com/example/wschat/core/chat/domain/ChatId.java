package com.example.wschat.core.chat.domain;

/** Identificador de uma conversa; corresponde ao {@code conversationId} usado pelo cliente. */
public record ChatId(String value) {

    public ChatId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ChatId não pode ser nulo ou vazio");
        }
    }

    public static ChatId of(String value) {
        return new ChatId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
