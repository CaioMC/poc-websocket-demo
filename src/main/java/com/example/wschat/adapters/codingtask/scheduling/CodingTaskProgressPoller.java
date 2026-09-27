package com.example.wschat.adapters.codingtask.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.wschat.core.codingtask.usecase.TrackCodingTasksUseCase;

/**
 * Adapter de agendamento: de tempos em tempos pede ao core para acompanhar as tarefas.
 * Sem tarefas ativas, a chamada não faz nada (nenhuma requisição ao GitHub).
 */
@Component
public class CodingTaskProgressPoller {

    private final TrackCodingTasksUseCase trackCodingTasks;

    public CodingTaskProgressPoller(TrackCodingTasksUseCase trackCodingTasks) {
        this.trackCodingTasks = trackCodingTasks;
    }

    @Scheduled(fixedDelayString = "${app.coding-agent.poll-interval-ms:15000}",
               initialDelayString = "${app.coding-agent.poll-interval-ms:15000}")
    public void poll() {
        this.trackCodingTasks.refreshActiveTasks();
    }
}
