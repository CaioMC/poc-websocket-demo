package com.example.wschat.adapters.codingtask.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do gatilho do agente de codificação (prefixo {@code app.coding-agent}).
 *
 * @param repository      repositório alvo padrão (owner/repo); precisa ter o workflow coding-agent.yml
 * @param baseBranch      branch base das tarefas
 * @param workflowFile    nome do arquivo do workflow no repositório alvo
 * @param pollIntervalMs  intervalo entre consultas ao GitHub
 * @param dispatchTimeout quanto esperar a execução aparecer antes de marcar a tarefa como falha
 * @param contextMessages quantas mensagens recentes da conversa vão para a issue
 * @param github          acesso à API do GitHub
 */
@ConfigurationProperties(prefix = "app.coding-agent")
public record CodingAgentProperties(
    String repository,
    String baseBranch,
    String workflowFile,
    long pollIntervalMs,
    Duration dispatchTimeout,
    int contextMessages,
    Github github
) {

    /**
     * @param apiUrl API do GitHub (troque para GitHub Enterprise Server, se for o caso)
     * @param token  token fine-grained com Issues (RW), Actions (RW) e Metadata (R) no repositório alvo
     */
    public record Github(String apiUrl, String token) {
    }
}
