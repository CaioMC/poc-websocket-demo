package com.example.wschat.core.codingtask.usecase;

/**
 * Acompanha as tarefas em andamento: consulta o executor e, quando algo muda,
 * avisa o chat. Chamado periodicamente por um agendador.
 */
public interface TrackCodingTasksUseCase {

    void refreshActiveTasks();
}
