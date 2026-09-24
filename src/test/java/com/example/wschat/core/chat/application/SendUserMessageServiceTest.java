package com.example.wschat.core.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

@ExtendWith(MockitoExtension.class)
class SendUserMessageServiceTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");

    @Mock
    private ChatRepository repository;

    @Mock
    private ChatEventPublisher events;

    @Mock
    private ChatReasoningRunner reasoningRunner;

    private SendUserMessageService service;
    private Chat chat;

    @BeforeEach
    void setUp() {
        chat = Chat.startNew(CHAT_ID);
        when(repository.findOrCreate(CHAT_ID)).thenReturn(chat);
        service = new SendUserMessageService(repository, events, reasoningRunner);
    }

    @Test
    void deveAdicionarMensagemDoUsuarioEDispararRaciocinio() {
        service.send(CHAT_ID, "Preciso de ajuda");

        assertThat(chat.history()).hasSize(1);
        assertThat(chat.history().get(0).content()).isEqualTo("Preciso de ajuda");

        verify(events).publishMessageAppended(CHAT_ID, chat.history().get(0));
        verify(reasoningRunner).runAsync(chat);
    }

    @Test
    void deveInterromperRodadaAnteriorAntesDeIniciarUmaNova() {
        // Ordem importa: interromper primeiro preserva o parcial da rodada
        // anterior no histórico ANTES da nova pergunta do usuário ser anexada.
        chat.changeStatus(ChatStatus.PROCESSING);
        when(reasoningRunner.interrupt(CHAT_ID)).thenReturn(true);

        service.send(CHAT_ID, "Na verdade, quero outra coisa");

        InOrder order = inOrder(reasoningRunner);
        order.verify(reasoningRunner).interrupt(CHAT_ID);
        order.verify(reasoningRunner).runAsync(chat);

        assertThat(chat.history()).hasSize(1);
        assertThat(chat.history().get(0).content()).isEqualTo("Na verdade, quero outra coisa");
    }

    @Test
    void deveIniciarNormalmenteQuandoNaoHaviaRodadaEmAndamento() {
        when(reasoningRunner.interrupt(CHAT_ID)).thenReturn(false);

        service.send(CHAT_ID, "Oi");

        verify(reasoningRunner).interrupt(CHAT_ID);
        verify(reasoningRunner).runAsync(chat);
    }
}
