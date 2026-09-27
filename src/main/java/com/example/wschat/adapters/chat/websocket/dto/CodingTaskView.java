package com.example.wschat.adapters.chat.websocket.dto;

import java.time.Instant;
import java.util.List;

import com.example.wschat.core.codingtask.domain.CodingTask;

/** Representação JSON de uma {@link CodingTask} para o cliente WebSocket (o "cartão" da tarefa). */
public record CodingTaskView(
    String id,
    String status,
    String repository,
    String title,
    Integer issueNumber,
    String issueUrl,
    String workBranch,
    String runUrl,
    String prUrl,
    String agentStatus,
    String summary,
    List<String> changedFiles,
    String verification,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

    public static CodingTaskView from(CodingTask task) {
        var result = task.result();
        return new CodingTaskView(
            task.id().value(),
            task.status().name(),
            task.repository(),
            task.title(),
            task.issueNumber(),
            task.issueUrl(),
            task.issueNumber() == null ? null : task.workBranch(),
            task.runUrl(),
            result.prUrl(),
            result.agentStatus(),
            result.summary(),
            result.changedFiles(),
            result.verification(),
            task.errorMessage(),
            task.createdAt(),
            task.updatedAt()
        );
    }
}
