package com.example.wschat.adapters.chat.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import com.example.wschat.core.chat.application.ChatReasoningRunner;
import com.example.wschat.core.chat.domain.ChatMessage;
import com.example.wschat.core.chat.domain.ai.ReasoningModelPort;

/** Implementação de {@link ReasoningModelPort} usando Spring AI com um modelo Ollama local. */
@Component
public class OllamaReasoningModelAdapter implements ReasoningModelPort {

    /** O prefixo instruído aqui é o mesmo que {@link ChatReasoningRunner#NEEDS_CONTEXT_PREFIX} verifica no core. */
    private static final String SYSTEM_PROMPT = """
        Você é um assistente de atendimento que raciocina em etapas para ajudar o usuário.
        Seja direto e responda sempre em português do Brasil.
        Se precisar de uma informação específica do usuário para continuar (ex.: número de
        pedido, CPF, data), inicie a resposta EXATAMENTE com a frase "%s:"
        seguida da pergunta objetiva. Caso contrário, responda normalmente.
        """.formatted(ChatReasoningRunner.NEEDS_CONTEXT_PREFIX);

    private final ChatClient chatClient;

    public OllamaReasoningModelAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<String> streamReply(List<ChatMessage> conversationHistory) {
        var messages = toSpringAiMessages(conversationHistory);
        return chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .messages(messages)
            .stream()
            .content();
    }

    private List<Message> toSpringAiMessages(List<ChatMessage> history) {
        return history.stream()
            .map(this::toSpringAiMessage)
            .toList();
    }

    private Message toSpringAiMessage(ChatMessage message) {
        return switch (message.role()) {
            case ASSISTANT -> new AssistantMessage(message.content());
            case SYSTEM -> new SystemMessage(message.content());
            case CONTEXT -> new UserMessage("Contexto adicional: " + message.content());
            case USER -> new UserMessage(message.content());
        };
    }
}
