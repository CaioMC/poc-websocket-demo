package com.example.wschat.core.codingtask.domain;

import java.time.Instant;

import com.example.wschat.core.chat.domain.ChatId;

/**
 * Aggregate root de um pedido de codificação feito a partir de uma conversa.
 *
 * Guarda o que o assistente sabe sobre o trabalho que acontece fora dele: a issue criada no
 * GitHub, a execução do workflow e, no fim, o resultado (PR, arquivos, verificação).
 * As transições de estado ficam aqui, para que as regras não se espalhem pelos serviços.
 */
public final class CodingTask {

    private final CodingTaskId id;
    private final ChatId chatId;
    private final String repository;
    private final String baseBranch;
    private final String title;
    private final String description;
    private final Instant createdAt;

    private volatile CodingTaskStatus status = CodingTaskStatus.REQUESTED;
    private volatile Integer issueNumber;
    private volatile String issueUrl;
    private volatile String runId;
    private volatile String runUrl;
    private volatile CodingTaskResult result = CodingTaskResult.empty();
    private volatile String errorMessage;
    private volatile Instant updatedAt;

    private CodingTask(CodingTaskId id, ChatId chatId, String repository, String baseBranch,
                       String title, String description, Instant createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static CodingTask request(ChatId chatId, String repository, String baseBranch, String title, String description) {
        if (repository == null || !repository.contains("/")) {
            throw new IllegalArgumentException("Repositório deve estar no formato owner/repo");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A tarefa precisa de um título");
        }
        return new CodingTask(CodingTaskId.newId(), chatId, repository, baseBranch, title, description, Instant.now());
    }

    /** Branch que o agente vai criar: previsível, para o humano achar e para o PR apontar. */
    public String workBranch() {
        var issuePart = issueNumber == null ? "task" : "issue-" + issueNumber;
        return "agent/" + issuePart + "-" + id.value();
    }

    public void issueOpened(int number, String url) {
        this.issueNumber = number;
        this.issueUrl = url;
        changeStatus(CodingTaskStatus.ISSUE_OPENED);
    }

    public void dispatched() {
        changeStatus(CodingTaskStatus.QUEUED);
    }

    /** @return true se algo mudou (para o serviço só publicar eventos quando necessário). */
    public boolean runFound(String newRunId, String newRunUrl) {
        if (newRunId == null || newRunId.equals(this.runId)) {
            return false;
        }
        this.runId = newRunId;
        this.runUrl = newRunUrl;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean started() {
        if (status == CodingTaskStatus.RUNNING) {
            return false;
        }
        changeStatus(CodingTaskStatus.RUNNING);
        return true;
    }

    public void completed(CodingTaskResult finalResult) {
        this.result = finalResult == null ? CodingTaskResult.empty() : finalResult;
        changeStatus(CodingTaskStatus.COMPLETED);
    }

    public void failed(String reason, CodingTaskResult partialResult) {
        this.errorMessage = reason;
        if (partialResult != null) {
            this.result = partialResult;
        }
        changeStatus(CodingTaskStatus.FAILED);
    }

    private void changeStatus(CodingTaskStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public CodingTaskId id() { return id; }
    public ChatId chatId() { return chatId; }
    public String repository() { return repository; }
    public String baseBranch() { return baseBranch; }
    public String title() { return title; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public CodingTaskStatus status() { return status; }
    public Integer issueNumber() { return issueNumber; }
    public String issueUrl() { return issueUrl; }
    public String runId() { return runId; }
    public String runUrl() { return runUrl; }
    public CodingTaskResult result() { return result; }
    public String errorMessage() { return errorMessage; }
}
