package com.example.wschat.core.chat.application;

import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.chat.usecase.ResetChatUseCase;

@Service
public class ResetChatService implements ResetChatUseCase {

    private final ChatRepository repository;
    private final ChatEventPublisher events;
    private final ChatReasoningRunner reasoningRunner;

    public ResetChatService(ChatRepository repository, ChatEventPublisher events, ChatReasoningRunner reasoningRunner) {
        this.repository = repository;
        this.events = events;
        this.reasoningRunner = reasoningRunner;
    }

    @Override
    public void reset(ChatId chatId) {
        // Evita que uma geração em andamento reapareça no histórico recém-limpo.
        this.reasoningRunner.discardActiveReasoning(chatId);

        this.repository.reset(chatId);
        this.events.publishStatusChanged(chatId, ChatStatus.IDLE);
    }
}
