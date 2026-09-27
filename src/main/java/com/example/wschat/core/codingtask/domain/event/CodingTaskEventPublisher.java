package com.example.wschat.core.codingtask.domain.event;

import com.example.wschat.core.codingtask.domain.CodingTask;

/** Porta de saída: como o core avisa o mundo externo que uma tarefa de codificação mudou. */
public interface CodingTaskEventPublisher {

    void publishTaskUpdated(CodingTask task);
}
