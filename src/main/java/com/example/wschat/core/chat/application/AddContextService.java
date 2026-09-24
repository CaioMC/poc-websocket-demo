package com.example.wschat.core.chat.application;

import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.chat.usecase.AddContextUseCase;

@Service
public class AddContextService implements AddContextUseCase {

    private final ChatRepository repository;
    private final ChatEventPublisher events;
    private final ChatReasoningRunner reasoningRunner;

    public AddContextService(ChatRepository repository, ChatEventPublisher events, ChatReasoningRunner reasoningRunner) {
        this.repository = repository;
        this.events = events;
        this.reasoningRunner = reasoningRunner;
    }

    @Override
    public void addContext(ChatId chatId, String content) {
        var chat = this.repository.findOrCreate(chatId);

        var message = chat.appendMessage(ChatRole.CONTEXT, content);
        this.repository.save(chat);
        this.events.publishMessageAppended(chatId, message);

        if (chat.status() == ChatStatus.WAITING_FOR_CONTEXT) {
            this.reasoningRunner.runAsync(chat);
        }
    }
}
