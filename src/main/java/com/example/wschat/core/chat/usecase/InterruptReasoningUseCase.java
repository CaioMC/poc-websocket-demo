package com.example.wschat.core.chat.usecase;

import com.example.wschat.core.chat.domain.ChatId;

/** Para a geração em andamento, como o botão "parar" do ChatGPT/Claude. */
public interface InterruptReasoningUseCase {

    /** @return {@code true} se havia uma geração em andamento e ela foi interrompida. */
    boolean interrupt(ChatId chatId);
}
