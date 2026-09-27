package com.example.wschat.core.codingtask.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.codingtask.domain.CodingTaskStatus;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.IssueRef;
import com.example.wschat.core.codingtask.domain.event.CodingTaskEventPublisher;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;

@ExtendWith(MockitoExtension.class)
class StartCodingTaskServiceTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");
    private static final CodingTaskSettings SETTINGS =
        new CodingTaskSettings("CaioMC/app", "main", Duration.ofMinutes(10), 6);

    @Mock
    private CodingAgentPort codingAgent;

    @Mock
    private CodingTaskRepository tasks;

    @Mock
    private ChatRepository chats;

    @Mock
    private ChatEventPublisher chatEvents;

    @Mock
    private CodingTaskEventPublisher taskEvents;

    private StartCodingTaskService service;
    private Chat chat;

    @BeforeEach
    void setUp() {
        chat = Chat.startNew(CHAT_ID);
        when(chats.findOrCreate(CHAT_ID)).thenReturn(chat);
        var notifier = new CodingTaskChatNotifier(chats, chatEvents, taskEvents);
        service = new StartCodingTaskService(codingAgent, tasks, chats, notifier, SETTINGS);
    }

    @Test
    void deveAbrirIssueComContextoDaConversaEDispararOWorkflow() {
        chat.appendMessage(ChatRole.USER, "Precisamos de um endpoint de saúde");
        chat.appendMessage(ChatRole.ASSISTANT, "Sugiro GET /api/health");
        when(codingAgent.openIssue(eq("CaioMC/app"), anyString(), anyString()))
            .thenReturn(new IssueRef(12, "https://github.com/CaioMC/app/issues/12"));

        var task = service.start(CHAT_ID, "Criar endpoint GET /api/health\n- [ ] responde 200");

        var body = ArgumentCaptor.forClass(String.class);
        verify(codingAgent).openIssue(eq("CaioMC/app"), eq("Criar endpoint GET /api/health"), body.capture());
        assertThat(body.getValue())
            .contains("- [ ] responde 200")
            .contains("Precisamos de um endpoint de saúde")
            .contains("Sugiro GET /api/health");

        verify(codingAgent).dispatch(task);
        assertThat(task.status()).isEqualTo(CodingTaskStatus.QUEUED);
        assertThat(task.issueNumber()).isEqualTo(12);
        assertThat(task.workBranch()).isEqualTo("agent/issue-12-" + task.id().value());

        // Pedido do usuário + aviso do assistente com os links ficam no histórico do chat.
        assertThat(chat.history()).hasSize(4);
        assertThat(chat.history().get(2).content()).startsWith("/codificar Criar endpoint");
        assertThat(chat.history().get(3).role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(chat.history().get(3).content()).contains("issue #12").contains(task.workBranch());
    }

    @Test
    void deveAceitarOutroRepositorioNoPedido() {
        when(codingAgent.openIssue(eq("CaioMC/outro"), anyString(), anyString()))
            .thenReturn(new IssueRef(3, "https://github.com/CaioMC/outro/issues/3"));

        var task = service.start(CHAT_ID, "repo=CaioMC/outro Corrigir bug do login");

        assertThat(task.repository()).isEqualTo("CaioMC/outro");
        assertThat(task.title()).isEqualTo("Corrigir bug do login");
    }

    @Test
    void deveAvisarNoChatQuandoOGitHubFalhar() {
        when(codingAgent.openIssue(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("token do GitHub não configurado"));

        var task = service.start(CHAT_ID, "Criar endpoint");

        assertThat(task.status()).isEqualTo(CodingTaskStatus.FAILED);
        verify(codingAgent, never()).dispatch(any());
        assertThat(chat.history().get(chat.history().size() - 1).content())
            .isEqualTo("Não consegui iniciar a codificação: token do GitHub não configurado");
    }

    @Test
    void deveRecusarPedidoVazio() {
        var task = service.start(CHAT_ID, "   ");

        assertThat(task).isNull();
        verify(codingAgent, never()).openIssue(anyString(), anyString(), anyString());
        assertThat(chat.history().get(chat.history().size() - 1).content()).contains("Descreva o que deve ser implementado");
    }
}
