package com.example.wschat.adapters.chat.websocket.event;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.wschat.adapters.chat.websocket.dto.ChatMessageView;
import com.example.wschat.adapters.chat.websocket.dto.CodingTaskView;
import com.example.wschat.adapters.chat.websocket.dto.OutgoingChatEvent;
import com.example.wschat.adapters.chat.websocket.session.ChatSessionRegistry;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatMessage;
import com.example.wschat.core.chat.domain.ChatSnapshot;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.event.CodingTaskEventPublisher;

/**
 * Implementação de {@link ChatEventPublisher} e de {@link CodingTaskEventPublisher}:
 * serializa eventos do core para JSON e envia via WebSocket. Um único adapter de saída
 * atende as duas portas, porque o canal (as sessões da conversa) é o mesmo.
 */
@Component
public class ChatWebSocketEventBroadcaster implements ChatEventPublisher, CodingTaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketEventBroadcaster.class);

    private final ChatSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public ChatWebSocketEventBroadcaster(ChatSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishMessageAppended(ChatId chatId, ChatMessage message) {
        broadcast(chatId, OutgoingChatEvent.messageAppended(ChatMessageView.from(message)));
    }

    @Override
    public void publishStatusChanged(ChatId chatId, ChatStatus status) {
        broadcast(chatId, OutgoingChatEvent.statusChanged(status.name()));
    }

    @Override
    public void publishReasoningChunk(ChatId chatId, String chunk) {
        broadcast(chatId, OutgoingChatEvent.reasoningChunk(chunk));
    }

    @Override
    public void publishReasoningCompleted(ChatId chatId) {
        broadcast(chatId, OutgoingChatEvent.reasoningCompleted());
    }

    @Override
    public void publishReasoningInterrupted(ChatId chatId) {
        broadcast(chatId, OutgoingChatEvent.reasoningInterrupted());
    }

    @Override
    public void publishError(ChatId chatId, String errorMessage) {
        broadcast(chatId, OutgoingChatEvent.error(errorMessage));
    }

    @Override
    public void publishTaskUpdated(CodingTask task) {
        broadcast(task.chatId(), OutgoingChatEvent.codingTaskUpdated(CodingTaskView.from(task)));
    }

    /** Envia o estado das tarefas de codificação só à sessão que acabou de (re)conectar. */
    public void sendCodingTasksTo(WebSocketSession session, List<CodingTask> tasks) {
        tasks.forEach(task -> send(session, OutgoingChatEvent.codingTaskUpdated(CodingTaskView.from(task))));
    }

    /** Envia o histórico + status atual só à sessão que acabou de (re)conectar. */
    public void sendReplayTo(WebSocketSession session, ChatSnapshot snapshot) {
        var messages = snapshot.messages().stream().map(ChatMessageView::from).toList();
        send(session, OutgoingChatEvent.replay(snapshot.status().name(), messages));
    }

    private void broadcast(ChatId chatId, OutgoingChatEvent event) {
        for (WebSocketSession session : sessionRegistry.activeSessions(chatId)) {
            send(session, event);
        }
    }

    private void send(WebSocketSession session, OutgoingChatEvent event) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        } catch (IOException e) {
            log.warn("Falha ao enviar mensagem WebSocket (sessão {}): {}", session.getId(), e.getMessage());
        }
    }
}
