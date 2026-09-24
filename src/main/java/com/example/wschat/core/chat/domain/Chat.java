package com.example.wschat.core.chat.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Aggregate root da conversa: identificador, status corrente e histórico de mensagens. */
public final class Chat {

    private final ChatId id;
    private final Instant createdAt;
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private volatile ChatStatus status = ChatStatus.IDLE;

    private Chat(ChatId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public static Chat startNew(ChatId id) {
        return new Chat(id, Instant.now());
    }

    public ChatId id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ChatStatus status() {
        return status;
    }

    public void changeStatus(ChatStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isProcessing() {
        return status == ChatStatus.PROCESSING;
    }

    public ChatMessage appendMessage(ChatRole role, String content) {
        var message = ChatMessage.of(role, content);
        messages.add(message);
        return message;
    }

    /** Preserva no histórico o trecho que o assistente gerou antes de ser interrompido. */
    public ChatMessage appendInterruptedAssistantMessage(String partialContent) {
        var message = ChatMessage.interrupted(ChatRole.ASSISTANT, partialContent);
        messages.add(message);
        return message;
    }

    public List<ChatMessage> history() {
        return Collections.unmodifiableList(messages);
    }

    public void clear() {
        messages.clear();
        status = ChatStatus.IDLE;
    }
}
