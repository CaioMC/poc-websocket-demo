package com.example.wschat.core.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

@ExtendWith(MockitoExtension.class)
class AddContextServiceTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");

    @Mock
    private ChatRepository repository;

    @Mock
    private ChatEventPublisher events;

    @Mock
    private ChatReasoningRunner reasoningRunner;

    private AddContextService service;
    private Chat chat;

    @BeforeEach
    void setUp() {
        chat = Chat.startNew(CHAT_ID);
        when(repository.findOrCreate(CHAT_ID)).thenReturn(chat);
        service = new AddContextService(repository, events, reasoningRunner);
    }

    @Test
    void deveApenasRegistrarContextoQuandoConversaNaoEstiverEsperandoPorEle() {
        service.addContext(CHAT_ID, "Informação qualquer");

        assertThat(chat.history()).hasSize(1);
        assertThat(chat.history().get(0).role()).isEqualTo(ChatRole.CONTEXT);
        verify(reasoningRunner, never()).runAsync(chat);
    }

    @Test
    void deveRetomarRaciocinioQuandoConversaEstiverEsperandoContexto() {
        chat.appendMessage(ChatRole.USER, "Quero saber do meu pedido");
        chat.changeStatus(ChatStatus.WAITING_FOR_CONTEXT);

        service.addContext(CHAT_ID, "12345");

        assertThat(chat.history()).extracting(m -> m.role())
            .containsExactly(ChatRole.USER, ChatRole.CONTEXT);
        verify(reasoningRunner).runAsync(chat);
    }
}
