package com.example.wschat.core.codingtask.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.CodingTaskResult;
import com.example.wschat.core.codingtask.domain.CodingTaskStatus;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.RunProgress;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.RunProgress.Phase;
import com.example.wschat.core.codingtask.domain.event.CodingTaskEventPublisher;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;

@ExtendWith(MockitoExtension.class)
class TrackCodingTasksServiceTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");

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

    private TrackCodingTasksService service;
    private CodingTask task;
    private Chat chat;

    @BeforeEach
    void setUp() {
        chat = Chat.startNew(CHAT_ID);
        task = CodingTask.request(CHAT_ID, "CaioMC/app", "main", "Criar endpoint", "Criar endpoint");
        task.issueOpened(12, "https://github.com/CaioMC/app/issues/12");
        task.dispatched();
        var notifier = new CodingTaskChatNotifier(chats, chatEvents, taskEvents);
        var settings = new CodingTaskSettings("CaioMC/app", "main", Duration.ofMinutes(10), 6);
        service = new TrackCodingTasksService(codingAgent, tasks, notifier, settings);
        when(tasks.findActive()).thenReturn(List.of(task));
    }

    @Test
    void deveMarcarComoRodandoQuandoAExecucaoComecar() {
        when(codingAgent.checkProgress(task)).thenReturn(new RunProgress(Phase.RUNNING, "99", "https://run/99", null));

        service.refreshActiveTasks();

        assertThat(task.status()).isEqualTo(CodingTaskStatus.RUNNING);
        assertThat(task.runUrl()).isEqualTo("https://run/99");
        verify(taskEvents).publishTaskUpdated(task);
        verify(chatEvents, never()).publishMessageAppended(any(), any());
    }

    @Test
    void naoDevePublicarNadaSeNadaMudou() {
        when(codingAgent.checkProgress(task)).thenReturn(RunProgress.notFound());

        service.refreshActiveTasks();

        assertThat(task.status()).isEqualTo(CodingTaskStatus.QUEUED);
        verify(taskEvents, never()).publishTaskUpdated(any());
    }

    @Test
    void deveAvisarNoChatComOLinkDoPrAoTerminar() {
        when(chats.findOrCreate(CHAT_ID)).thenReturn(chat);
        var result = new CodingTaskResult("COMPLETED", "Criei o endpoint e o teste.",
            "https://github.com/CaioMC/app/pull/13", List.of("src/Health.java"), "bash .agent/verify.sh terminou com código 0");
        when(codingAgent.checkProgress(task)).thenReturn(new RunProgress(Phase.SUCCEEDED, "99", "https://run/99", result));

        service.refreshActiveTasks();

        assertThat(task.status()).isEqualTo(CodingTaskStatus.COMPLETED);
        var message = chat.history().get(chat.history().size() - 1).content();
        assertThat(message)
            .contains("https://github.com/CaioMC/app/pull/13")
            .contains("- src/Health.java")
            .contains("código 0")
            .contains("Criei o endpoint e o teste.");
    }

    @Test
    void deveContinuarTentandoSeOGitHubFalharPontualmente() {
        when(codingAgent.checkProgress(task)).thenThrow(new IllegalStateException("timeout"));

        service.refreshActiveTasks();

        assertThat(task.status()).isEqualTo(CodingTaskStatus.QUEUED);
    }
}
