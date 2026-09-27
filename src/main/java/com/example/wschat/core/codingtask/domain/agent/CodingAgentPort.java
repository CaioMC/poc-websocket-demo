package com.example.wschat.core.codingtask.domain.agent;

import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.CodingTaskResult;

/**
 * Porta de saída para o agente de codificação que roda FORA do assistente.
 *
 * O core só sabe três coisas: abrir uma tarefa, pedir para executá-la e perguntar como está.
 * A implementação atual usa GitHub (issue + GitHub Actions), mas o core não sabe disso:
 * trocar por outro executor não muda nenhuma regra de negócio.
 */
public interface CodingAgentPort {

    /** Registra a tarefa onde o agente vai buscá-la (no GitHub: uma issue). */
    IssueRef openIssue(String repository, String title, String body);

    /** Pede a execução do agente para a tarefa (no GitHub: dispara o workflow). */
    void dispatch(CodingTask task);

    /** Consulta o andamento. Quando terminar, traz o resultado publicado pelo agente. */
    RunProgress checkProgress(CodingTask task);

    record IssueRef(int number, String url) {
    }

    record RunProgress(Phase phase, String runId, String runUrl, CodingTaskResult result) {

        public enum Phase {
            /** A execução ainda não apareceu na API (acabou de ser disparada). */
            NOT_FOUND,
            QUEUED,
            RUNNING,
            SUCCEEDED,
            FAILED
        }

        public static RunProgress notFound() {
            return new RunProgress(Phase.NOT_FOUND, null, null, null);
        }
    }
}
