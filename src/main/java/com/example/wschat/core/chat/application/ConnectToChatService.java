package com.example.wschat.core.chat.application;

import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatSnapshot;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.chat.usecase.ConnectToChatUseCase;

@Service
public class ConnectToChatService implements ConnectToChatUseCase {

    private final ChatRepository repository;

    public ConnectToChatService(ChatRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatSnapshot connect(ChatId chatId) {
        var chat = repository.findOrCreate(chatId);
        return ChatSnapshot.of(chat);
    }
}
