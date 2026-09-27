package com.example.wschat.core.codingtask.domain.repository;

import java.util.List;
import java.util.Optional;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.CodingTaskId;

/** Porta de persistência de {@link CodingTask}. */
public interface CodingTaskRepository {

    void save(CodingTask task);

    Optional<CodingTask> findById(CodingTaskId id);

    List<CodingTask> findByChat(ChatId chatId);

    /** Tarefas que ainda estão rodando no GitHub e precisam ser acompanhadas. */
    List<CodingTask> findActive();
}
