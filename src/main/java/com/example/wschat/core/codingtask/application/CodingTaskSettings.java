package com.example.wschat.core.codingtask.application;

import java.time.Duration;

/**
 * Parâmetros de negócio das tarefas de codificação, montados pela configuração da aplicação.
 *
 * @param defaultRepository repositório alvo quando o usuário não informa outro (owner/repo)
 * @param baseBranch        branch de onde o agente parte e para onde o PR aponta
 * @param dispatchTimeout   quanto esperar a execução aparecer no GitHub antes de desistir
 * @param contextMessages   quantas mensagens recentes da conversa vão para a issue como contexto
 */
public record CodingTaskSettings(
    String defaultRepository,
    String baseBranch,
    Duration dispatchTimeout,
    int contextMessages
) {
}
