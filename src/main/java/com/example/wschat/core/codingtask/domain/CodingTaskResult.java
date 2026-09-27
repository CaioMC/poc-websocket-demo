package com.example.wschat.core.codingtask.domain;

import java.util.List;

/**
 * O que o agente devolveu, lido do artefato {@code agent-result} do workflow (result.json).
 *
 * @param agentStatus  status do próprio agente: COMPLETED, VERIFICATION_FAILED, INCOMPLETE...
 * @param summary      resumo escrito pelo modelo para o revisor
 * @param prUrl        link do PR em rascunho, se foi aberto
 * @param changedFiles arquivos alterados
 * @param verification resultado da verificação independente, em texto
 */
public record CodingTaskResult(
    String agentStatus,
    String summary,
    String prUrl,
    List<String> changedFiles,
    String verification
) {

    public CodingTaskResult {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }

    public static CodingTaskResult empty() {
        return new CodingTaskResult(null, null, null, List.of(), null);
    }
}
