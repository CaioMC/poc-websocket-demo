package com.example.wschat.core.chat.domain.event;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatMessage;
import com.example.wschat.core.chat.domain.ChatStatus;

/** Porta de saída: como o core notifica o mundo externo sobre mudanças em uma conversa. */
public interface ChatEventPublisher {

    void publishMessageAppended(ChatId chatId, ChatMessage message);

    void publishStatusChanged(ChatId chatId, ChatStatus status);

    void publishReasoningChunk(ChatId chatId, String chunk);

    void publishReasoningCompleted(ChatId chatId);

    void publishReasoningInterrupted(ChatId chatId);

    void publishError(ChatId chatId, String errorMessage);
}
