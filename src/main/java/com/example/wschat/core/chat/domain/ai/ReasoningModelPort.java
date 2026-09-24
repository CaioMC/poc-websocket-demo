package com.example.wschat.core.chat.domain.ai;

import java.util.List;

import reactor.core.publisher.Flux;

import com.example.wschat.core.chat.domain.ChatMessage;

/** Porta para o modelo de linguagem que gera as respostas da conversa, em streaming. */
public interface ReasoningModelPort {

    Flux<String> streamReply(List<ChatMessage> conversationHistory);
}
