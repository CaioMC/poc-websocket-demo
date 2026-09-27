package com.example.wschat.adapters.chat.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.wschat.adapters.chat.websocket.dto.IncomingChatMessage;
import com.example.wschat.adapters.chat.websocket.event.ChatWebSocketEventBroadcaster;
import com.example.wschat.adapters.chat.websocket.session.ChatSessionRegistry;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.usecase.AddContextUseCase;
import com.example.wschat.core.chat.usecase.ConnectToChatUseCase;
import com.example.wschat.core.chat.usecase.InterruptReasoningUseCase;
import com.example.wschat.core.chat.usecase.ResetChatUseCase;
import com.example.wschat.core.chat.usecase.SendUserMessageUseCase;
import com.example.wschat.core.codingtask.usecase.ListCodingTasksUseCase;
import com.example.wschat.core.codingtask.usecase.StartCodingTaskUseCase;

/**
 * Adapter fino de WebSocket (sem STOMP): ciclo de vida da conexão e tradução
 * de mensagens JSON para chamadas aos use cases do core — nenhuma regra de
 * negócio vive aqui.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String CONVERSATION_ID_PARAM = "conversationId";
    private static final String DEFAULT_CONVERSATION_ID = "default";

    private final ChatSessionRegistry sessionRegistry;
    private final ChatWebSocketEventBroadcaster broadcaster;
    private final ConnectToChatUseCase connectToChatUseCase;
    private final SendUserMessageUseCase sendUserMessageUseCase;
    private final AddContextUseCase addContextUseCase;
    private final InterruptReasoningUseCase interruptReasoningUseCase;
    private final ResetChatUseCase resetChatUseCase;
    private final StartCodingTaskUseCase startCodingTaskUseCase;
    private final ListCodingTasksUseCase listCodingTasksUseCase;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(
        ChatSessionRegistry sessionRegistry,
        ChatWebSocketEventBroadcaster broadcaster,
        ConnectToChatUseCase connectToChatUseCase,
        SendUserMessageUseCase sendUserMessageUseCase,
        AddContextUseCase addContextUseCase,
        InterruptReasoningUseCase interruptReasoningUseCase,
        ResetChatUseCase resetChatUseCase,
        StartCodingTaskUseCase startCodingTaskUseCase,
        ListCodingTasksUseCase listCodingTasksUseCase,
        ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.broadcaster = broadcaster;
        this.connectToChatUseCase = connectToChatUseCase;
        this.sendUserMessageUseCase = sendUserMessageUseCase;
        this.addContextUseCase = addContextUseCase;
        this.interruptReasoningUseCase = interruptReasoningUseCase;
        this.resetChatUseCase = resetChatUseCase;
        this.startCodingTaskUseCase = startCodingTaskUseCase;
        this.listCodingTasksUseCase = listCodingTasksUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var chatId = this.extractChatId(session);
        this.sessionRegistry.register(chatId, session);

        log.info("[{}] cliente conectado (conexões ativas: {})", chatId, this.sessionRegistry.countActiveSessions(chatId));

        var snapshot = this.connectToChatUseCase.connect(chatId);
        this.broadcaster.sendReplayTo(session, snapshot);
        // Depois do histórico, o estado das tarefas do agente de codificação desta conversa.
        this.broadcaster.sendCodingTasksTo(session, this.listCodingTasksUseCase.list(chatId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var chatId = this.extractChatId(session);
        this.sessionRegistry.unregister(chatId, session);

        log.info("[{}] cliente desconectado (conexões ativas: {})", chatId, this.sessionRegistry.countActiveSessions(chatId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        var chatId = this.extractChatId(session);
        var incoming = this.objectMapper.readValue(message.getPayload(), IncomingChatMessage.class);

        switch (incoming.type()) {
        case "user_message" -> this.sendUserMessageUseCase.send(chatId, incoming.contentOrEmpty());
        case "context" -> this.addContextUseCase.addContext(chatId, incoming.contentOrEmpty());
        case "interrupt" -> this.interruptReasoningUseCase.interrupt(chatId);
        case "reset" -> this.resetChatUseCase.reset(chatId);
        case "coding_task" -> this.startCodingTaskUseCase.start(chatId, incoming.contentOrEmpty());
        default -> log.warn("[{}] mensagem desconhecida: {}", chatId, incoming.type());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[{}] erro de transporte no WebSocket: {}", this.extractChatId(session), exception.getMessage());
    }

    private ChatId extractChatId(WebSocketSession session) {
        var params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        var values = params.get(CONVERSATION_ID_PARAM);
        var value = (values == null || values.isEmpty() || values.get(0).isBlank())
            ? DEFAULT_CONVERSATION_ID
            : values.get(0);
        return ChatId.of(value);
    }
}
