package com.example.wschat.core.codingtask.usecase;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;

/**
 * Transforma um pedido do usuário no chat em trabalho para o agente de codificação:
 * abre a issue com o contexto da conversa e dispara o workflow.
 */
public interface StartCodingTaskUseCase {

    CodingTask start(ChatId chatId, String request);
}
