package com.example.wschat.core.chat.usecase;

import com.example.wschat.core.chat.domain.ChatId;

/** Envia uma nova mensagem do usuário, disparando uma rodada de raciocínio do modelo. */
public interface SendUserMessageUseCase {

    void send(ChatId chatId, String content);
}
