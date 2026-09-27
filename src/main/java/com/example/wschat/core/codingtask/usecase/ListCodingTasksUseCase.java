package com.example.wschat.core.codingtask.usecase;

import java.util.List;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;

/** Lista as tarefas de uma conversa (usado ao conectar/reconectar, junto com o replay). */
public interface ListCodingTasksUseCase {

    List<CodingTask> list(ChatId chatId);
}
