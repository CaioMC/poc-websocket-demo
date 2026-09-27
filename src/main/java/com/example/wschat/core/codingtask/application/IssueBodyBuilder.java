package com.example.wschat.core.codingtask.application;

import java.util.List;

import com.example.wschat.core.chat.domain.ChatMessage;
import com.example.wschat.core.chat.domain.ChatRole;

/**
 * Monta o corpo da issue. É aqui que o contexto do assistente vira insumo para o agente:
 * o pedido do usuário e as últimas mensagens da conversa.
 *
 * Linhas "- [ ] ..." escritas pelo usuário no pedido são mantidas e o agente as trata
 * como critérios de aceite.
 */
final class IssueBodyBuilder {

    private static final int MAX_MESSAGE_CHARS = 600;

    private IssueBodyBuilder() {
    }

    static String build(String description, List<ChatMessage> recentConversation) {
        var body = new StringBuilder();
        body.append("## Pedido\n\n").append(description.strip()).append("\n");

        var context = recentConversation.stream()
            .filter(message -> message.role() != ChatRole.SYSTEM)
            .toList();
        if (!context.isEmpty()) {
            body.append("\n## Contexto da conversa com o assistente\n\n");
            for (var message : context) {
                body.append("> **").append(label(message.role())).append(":** ")
                    .append(truncate(message.content()).replace("\n", "\n> "))
                    .append("\n>\n");
            }
        }

        body.append("\n---\n_Issue criada pelo assistente para o agente de codificação. ")
            .append("O agente vai abrir um PR em rascunho ligado a esta issue._\n");
        return body.toString();
    }

    private static String label(ChatRole role) {
        return switch (role) {
            case USER -> "Usuário";
            case CONTEXT -> "Contexto adicional";
            case ASSISTANT -> "Assistente";
            case SYSTEM -> "Sistema";
        };
    }

    private static String truncate(String content) {
        return content.length() > MAX_MESSAGE_CHARS ? content.substring(0, MAX_MESSAGE_CHARS) + "..." : content;
    }
}
