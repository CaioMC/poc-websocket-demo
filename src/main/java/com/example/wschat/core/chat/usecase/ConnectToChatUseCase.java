package com.example.wschat.core.chat.usecase;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatSnapshot;

/** Obtém (criando se necessário) o estado atual de uma conversa para um cliente que acabou de conectar. */
public interface ConnectToChatUseCase {

    ChatSnapshot connect(ChatId chatId);
}
