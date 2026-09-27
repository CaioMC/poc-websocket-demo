package com.example.wschat.core.codingtask.application;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.RunProgress;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;
import com.example.wschat.core.codingtask.usecase.TrackCodingTasksUseCase;

/**
 * Acompanha as tarefas que estão no GitHub Actions.
 *
 * O navegador não faz polling: quem consulta o GitHub é o servidor, de tempos em tempos,
 * e só publica eventos no WebSocket quando algo muda. É a mesma ideia do resto da POC:
 * o estado vive no servidor e chega ao cliente por eventos.
 */
@Service
public class TrackCodingTasksService implements TrackCodingTasksUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrackCodingTasksService.class);

    private final CodingAgentPort codingAgent;
    private final CodingTaskRepository tasks;
    private final CodingTaskChatNotifier notifier;
    private final CodingTaskSettings settings;

    public TrackCodingTasksService(CodingAgentPort codingAgent, CodingTaskRepository tasks,
                                   CodingTaskChatNotifier notifier, CodingTaskSettings settings) {
        this.codingAgent = codingAgent;
        this.tasks = tasks;
        this.notifier = notifier;
        this.settings = settings;
    }

    @Override
    public void refreshActiveTasks() {
        for (var task : this.tasks.findActive()) {
            try {
                this.refresh(task);
            } catch (RuntimeException e) {
                // Falha pontual de rede não encerra a tarefa: tenta de novo na próxima rodada.
                log.warn("[{}] falha ao consultar tarefa {}: {}", task.chatId(), task.id(), e.getMessage());
            }
        }
    }

    void refresh(CodingTask task) {
        RunProgress progress = this.codingAgent.checkProgress(task);
        boolean changed = task.runFound(progress.runId(), progress.runUrl());

        switch (progress.phase()) {
            case NOT_FOUND -> {
                if (Duration.between(task.updatedAt(), Instant.now()).compareTo(this.settings.dispatchTimeout()) > 0) {
                    task.failed("a execução do workflow não apareceu no GitHub. Confira se o arquivo "
                        + "coding-agent.yml existe na branch base e se o token tem permissão de Actions.", null);
                    this.finish(task);
                    return;
                }
            }
            case QUEUED -> { /* continua aguardando na fila */ }
            case RUNNING -> changed |= task.started();
            case SUCCEEDED -> {
                task.completed(progress.result());
                this.finish(task);
                return;
            }
            case FAILED -> {
                task.failed("o workflow terminou com falha. Veja o log da execução.", progress.result());
                this.finish(task);
                return;
            }
        }

        if (changed) {
            this.tasks.save(task);
            this.notifier.taskChanged(task);
        }
    }

    private void finish(CodingTask task) {
        this.tasks.save(task);
        this.notifier.taskChanged(task);
        this.notifier.finished(task);
        log.info("[{}] tarefa {} finalizada: {}", task.chatId(), task.id(), task.status());
    }
}
