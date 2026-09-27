package com.example.wschat.core.codingtask.domain;

import java.util.UUID;

/**
 * Identificador curto de uma tarefa de codificação.
 *
 * É o {@code request_id} enviado ao workflow do GitHub Actions: o workflow usa
 * {@code run-name: coding-agent <id>}, e é por esse nome que o assistente encontra a execução.
 */
public record CodingTaskId(String value) {

    public CodingTaskId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CodingTaskId não pode ser nulo ou vazio");
        }
    }

    public static CodingTaskId newId() {
        return new CodingTaskId(UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String toString() {
        return value;
    }
}
