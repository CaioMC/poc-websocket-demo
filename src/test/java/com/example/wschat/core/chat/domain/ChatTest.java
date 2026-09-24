package com.example.wschat.core.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatTest {

    @Test
    void deveIniciarNoStatusIdleESemHistorico() {
        var chat = Chat.startNew(ChatId.of("chat-1"));

        assertThat(chat.status()).isEqualTo(ChatStatus.IDLE);
        assertThat(chat.history()).isEmpty();
    }

    @Test
    void deveAcumularMensagensNoHistoricoEmOrdem() {
        var chat = Chat.startNew(ChatId.of("chat-1"));

        chat.appendMessage(ChatRole.USER, "Olá");
        chat.appendMessage(ChatRole.ASSISTANT, "Como posso ajudar?");

        assertThat(chat.history()).hasSize(2);
        assertThat(chat.history().get(0).role()).isEqualTo(ChatRole.USER);
        assertThat(chat.history().get(1).role()).isEqualTo(ChatRole.ASSISTANT);
    }

    @Test
    void deveExporIsProcessingApenasQuandoStatusForProcessing() {
        var chat = Chat.startNew(ChatId.of("chat-1"));
        assertThat(chat.isProcessing()).isFalse();

        chat.changeStatus(ChatStatus.PROCESSING);
        assertThat(chat.isProcessing()).isTrue();
    }

    @Test
    void clearDeveDescartarHistoricoEVoltarParaIdle() {
        var chat = Chat.startNew(ChatId.of("chat-1"));
        chat.appendMessage(ChatRole.USER, "Olá");
        chat.changeStatus(ChatStatus.PROCESSING);

        chat.clear();

        assertThat(chat.history()).isEmpty();
        assertThat(chat.status()).isEqualTo(ChatStatus.IDLE);
    }

    @Test
    void historyDeveSerImutavelParaQuemConsome() {
        var chat = Chat.startNew(ChatId.of("chat-1"));
        chat.appendMessage(ChatRole.USER, "Olá");

        var history = chat.history();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> history.add(ChatMessage.of(ChatRole.USER, "outra")));
    }
}
