package com.example.wschat.core.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

@ExtendWith(MockitoExtension.class)
class ConnectToChatServiceTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");

    @Mock
    private ChatRepository repository;

    private ConnectToChatService service;

    @BeforeEach
    void setUp() {
        service = new ConnectToChatService(repository);
    }

    @Test
    void deveRetornarSnapshotComHistoricoEStatusAtual() {
        var chat = Chat.startNew(CHAT_ID);
        chat.appendMessage(ChatRole.USER, "Olá");
        when(repository.findOrCreate(CHAT_ID)).thenReturn(chat);

        var snapshot = service.connect(CHAT_ID);

        assertThat(snapshot.id()).isEqualTo(CHAT_ID);
        assertThat(snapshot.status()).isEqualTo(chat.status());
        assertThat(snapshot.messages()).hasSize(1);
    }
}
