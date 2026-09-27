package com.example.wschat.core.codingtask.domain;

/**
 * Ciclo de vida de uma tarefa de codificação, do ponto de vista do assistente.
 *
 * <pre>
 * REQUESTED → ISSUE_OPENED → QUEUED → RUNNING → COMPLETED
 *                    ↘          ↘        ↘
 *                                 FAILED
 * </pre>
 */
public enum CodingTaskStatus {
    /** Pedido recebido do usuário, nada enviado ao GitHub ainda. */
    REQUESTED,
    /** Issue criada no GitHub; o workflow ainda vai ser disparado. */
    ISSUE_OPENED,
    /** Workflow disparado; aguardando a execução aparecer ou sair da fila. */
    QUEUED,
    /** O agente está trabalhando no GitHub Actions. */
    RUNNING,
    /** O workflow terminou com sucesso (normalmente com um PR em rascunho). */
    COMPLETED,
    /** Algo deu errado: ao criar a issue, ao disparar ou dentro do workflow. */
    FAILED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
