package com.example.wschat.core.chat.domain;

import java.util.List;

/** Foto imutável do estado de um {@link Chat} num dado instante, para expor ao mundo externo. */
public record ChatSnapshot(ChatId id, ChatStatus status, List<ChatMessage> messages) {

    public static ChatSnapshot of(Chat chat) {
        return new ChatSnapshot(chat.id(), chat.status(), chat.history());
    }
}
