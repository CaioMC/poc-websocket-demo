package com.example.wschat.core.chat.application;

import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.usecase.InterruptReasoningUseCase;

@Service
public class InterruptReasoningService implements InterruptReasoningUseCase {

    private final ChatReasoningRunner reasoningRunner;

    public InterruptReasoningService(ChatReasoningRunner reasoningRunner) {
        this.reasoningRunner = reasoningRunner;
    }

    @Override
    public boolean interrupt(ChatId chatId) {
        return this.reasoningRunner.interrupt(chatId);
    }
}
