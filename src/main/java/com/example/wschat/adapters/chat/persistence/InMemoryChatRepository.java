package com.example.wschat.adapters.chat.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

/** Implementação em memória de {@link ChatRepository} — sem banco de dados, adequada a esta POC. */
@Repository
public class InMemoryChatRepository implements ChatRepository {

    private final Map<String, Chat> chatsById = new ConcurrentHashMap<>();

    @Override
    public Chat findOrCreate(ChatId id) {
        return chatsById.computeIfAbsent(id.value(), key -> Chat.startNew(id));
    }

    @Override
    public void save(Chat chat) {
        chatsById.put(chat.id().value(), chat);
    }

    @Override
    public void reset(ChatId id) {
        var chat = Chat.startNew(id);
        chatsById.put(id.value(), chat);
    }
}
