package com.example.wschat.core.chat.usecase;

import com.example.wschat.core.chat.domain.ChatId;

/** Reinicia por completo o estado de uma conversa. */
public interface ResetChatUseCase {

    void reset(ChatId chatId);
}
