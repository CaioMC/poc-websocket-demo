package com.example.wschat.core.chat.domain.repository;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;

/** Porta de persistência de {@link Chat}. */
public interface ChatRepository {

    Chat findOrCreate(ChatId id);

    void save(Chat chat);

    void reset(ChatId id);
}
