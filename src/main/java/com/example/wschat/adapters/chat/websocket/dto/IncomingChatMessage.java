package com.example.wschat.adapters.chat.websocket.dto;

/**
 * Mensagem recebida do cliente pelo WebSocket. {@code type} é um de
 * {@code user_message}, {@code context}, {@code interrupt} ou {@code reset}
 * (ver {@code docs/websocket-explained.md}).
 */
public record IncomingChatMessage(String type, String content) {

    public String contentOrEmpty() {
        return content == null ? "" : content;
    }
}
