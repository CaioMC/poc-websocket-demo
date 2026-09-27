package com.example.wschat.adapters.chat.websocket.dto;

import java.util.List;

/**
 * Envelope JSON enviado ao cliente: um único tipo com campo {@code type}
 * discriminando o evento (ver {@code docs/websocket-explained.md}). Cada
 * fábrica estática só popula os campos daquele evento; os demais ficam nulos
 * e são omitidos na serialização ({@code ObjectMapper} com {@code NON_NULL}).
 */
public record OutgoingChatEvent(
    String type,
    String status,
    List<ChatMessageView> messages,
    ChatMessageView message,
    String content,
    String errorMessage,
    CodingTaskView codingTask
) {

    public static OutgoingChatEvent replay(String status, List<ChatMessageView> messages) {
        return new OutgoingChatEvent("replay", status, messages, null, null, null, null);
    }

    public static OutgoingChatEvent messageAppended(ChatMessageView message) {
        return new OutgoingChatEvent("message_appended", null, null, message, null, null, null);
    }

    public static OutgoingChatEvent statusChanged(String status) {
        return new OutgoingChatEvent("status_changed", status, null, null, null, null, null);
    }

    public static OutgoingChatEvent reasoningChunk(String content) {
        return new OutgoingChatEvent("reasoning_chunk", null, null, null, content, null, null);
    }

    public static OutgoingChatEvent reasoningCompleted() {
        return new OutgoingChatEvent("reasoning_completed", null, null, null, null, null, null);
    }

    public static OutgoingChatEvent reasoningInterrupted() {
        return new OutgoingChatEvent("reasoning_interrupted", null, null, null, null, null, null);
    }

    public static OutgoingChatEvent error(String errorMessage) {
        return new OutgoingChatEvent("error", null, null, null, null, errorMessage, null);
    }

    /** Estado atual de uma tarefa do agente de codificação (enviado a cada mudança e no replay). */
    public static OutgoingChatEvent codingTaskUpdated(CodingTaskView codingTask) {
        return new OutgoingChatEvent("coding_task_updated", null, null, null, null, null, codingTask);
    }
}
