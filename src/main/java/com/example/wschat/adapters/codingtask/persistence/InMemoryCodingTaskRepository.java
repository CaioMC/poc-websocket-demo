package com.example.wschat.adapters.codingtask.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.CodingTaskId;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;

/**
 * Implementação em memória de {@link CodingTaskRepository}, no mesmo espírito do repositório
 * de chats. Ao reiniciar o servidor as tarefas somem daqui, mas continuam no GitHub
 * (issue, execução e PR).
 */
@Repository
public class InMemoryCodingTaskRepository implements CodingTaskRepository {

    private final Map<CodingTaskId, CodingTask> tasksById = new ConcurrentHashMap<>();

    @Override
    public void save(CodingTask task) {
        tasksById.put(task.id(), task);
    }

    @Override
    public Optional<CodingTask> findById(CodingTaskId id) {
        return Optional.ofNullable(tasksById.get(id));
    }

    @Override
    public List<CodingTask> findByChat(ChatId chatId) {
        return tasksById.values().stream()
            .filter(task -> task.chatId().equals(chatId))
            .sorted(Comparator.comparing(CodingTask::createdAt))
            .toList();
    }

    @Override
    public List<CodingTask> findActive() {
        return tasksById.values().stream()
            .filter(task -> task.status().isActive())
            .toList();
    }
}
