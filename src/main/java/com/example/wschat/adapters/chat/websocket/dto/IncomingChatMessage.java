package com.example.wschat.adapters.chat.websocket.dto;

/**
 * Mensagem recebida do cliente pelo WebSocket. {@code type} é um de
 * {@code user_message}, {@code context}, {@code interrupt}, {@code reset} ou
 * {@code coding_task} (pedido para o agente de codificação; ver
 * {@code docs/websocket-explained.md} e {@code docs/coding-agent.md}).
 */
public record IncomingChatMessage(String type, String content) {

    public String contentOrEmpty() {
        return content == null ? "" : content;
    }
}
