package com.example.wschat.core.chat.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.usecase.SendUserMessageUseCase;

@Service
public class SendUserMessageService implements SendUserMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendUserMessageService.class);

    private final ChatRepository repository;
    private final ChatEventPublisher events;
    private final ChatReasoningRunner reasoningRunner;

    public SendUserMessageService(ChatRepository repository, ChatEventPublisher events, ChatReasoningRunner reasoningRunner) {
        this.repository = repository;
        this.events = events;
        this.reasoningRunner = reasoningRunner;
    }

    @Override
    public void send(ChatId chatId, String content) {
        // Interrompe ANTES de anexar a nova mensagem, para o histórico manter a
        // ordem natural: resposta parcial interrompida, só então a nova pergunta.
        if (this.reasoningRunner.interrupt(chatId)) {
            log.info("[{}] nova mensagem recebida durante o processamento: geração anterior interrompida", chatId);
        }

        var chat = this.repository.findOrCreate(chatId);
        var message = chat.appendMessage(ChatRole.USER, content);
        this.repository.save(chat);
        this.events.publishMessageAppended(chatId, message);

        this.reasoningRunner.runAsync(chat);
    }
}
