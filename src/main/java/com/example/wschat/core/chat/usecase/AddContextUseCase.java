package com.example.wschat.core.chat.usecase;

import com.example.wschat.core.chat.domain.ChatId;

/** Adiciona contexto complementar à conversa; retoma o raciocínio se ela estava aguardando por ele. */
public interface AddContextUseCase {

    void addContext(ChatId chatId, String content);
}
