package com.example.wschat.adapters.chat.websocket.session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.example.wschat.core.chat.domain.ChatId;

/** Registro das conexões WebSocket abertas por {@link ChatId} — pode haver 0, 1 ou N (ex.: várias abas). */
@Component
public class ChatSessionRegistry {

    private final Map<ChatId, Set<WebSocketSession>> sessionsByChat = new ConcurrentHashMap<>();

    public void register(ChatId chatId, WebSocketSession session) {
        sessionsByChat
            .computeIfAbsent(chatId, id -> new CopyOnWriteArraySet<>())
            .add(session);
    }

    public void unregister(ChatId chatId, WebSocketSession session) {
        var sessions = sessionsByChat.get(chatId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    public Set<WebSocketSession> activeSessions(ChatId chatId) {
        return sessionsByChat.getOrDefault(chatId, Set.of());
    }

    public int countActiveSessions(ChatId chatId) {
        return activeSessions(chatId).size();
    }
}
