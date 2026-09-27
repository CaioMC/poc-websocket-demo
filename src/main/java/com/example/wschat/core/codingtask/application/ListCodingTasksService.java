package com.example.wschat.core.codingtask.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;
import com.example.wschat.core.codingtask.usecase.ListCodingTasksUseCase;

@Service
public class ListCodingTasksService implements ListCodingTasksUseCase {

    private final CodingTaskRepository tasks;

    public ListCodingTasksService(CodingTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    public List<CodingTask> list(ChatId chatId) {
        return this.tasks.findByChat(chatId);
    }
}
