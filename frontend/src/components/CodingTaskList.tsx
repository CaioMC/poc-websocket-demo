import type { CodingTask, CodingTaskStatus } from '../types/chat';

const STATUS_LABEL: Record<CodingTaskStatus, string> = {
  REQUESTED: 'Preparando',
  ISSUE_OPENED: 'Issue criada',
  QUEUED: 'Na fila do GitHub Actions',
  RUNNING: 'Agente trabalhando',
  COMPLETED: 'Concluído',
  FAILED: 'Falhou',
};

type Tone = 'progress' | 'done' | 'error';

function toneOf(status: CodingTaskStatus): Tone {
  if (status === 'COMPLETED') return 'done';
  if (status === 'FAILED') return 'error';
  return 'progress';
}

/** Até quantas tarefas mostrar acima do composer (as mais recentes). */
const MAX_VISIBLE = 3;

/**
 * Cartões das tarefas do agente de codificação desta conversa.
 * Tudo aqui vem dos eventos `coding_task_updated` do servidor: o navegador não consulta o GitHub.
 */
export function CodingTaskList({ tasks }: { tasks: CodingTask[] }) {
  if (tasks.length === 0) {
    return null;
  }

  return (
    <section className="coding-tasks" aria-label="Tarefas do agente de codificação">
      {tasks.slice(0, MAX_VISIBLE).map((task) => (
        <CodingTaskCard key={task.id} task={task} />
      ))}
    </section>
  );
}

function CodingTaskCard({ task }: { task: CodingTask }) {
  const tone = toneOf(task.status);
  const files = task.changedFiles ?? [];

  return (
    <article className={`coding-task coding-task--${tone}`}>
      <header className="coding-task__header">
        <span className={`coding-task__status coding-task__status--${tone}`}>
          {tone === 'progress' && <span className="coding-task__spinner" aria-hidden="true" />}
          {STATUS_LABEL[task.status]}
        </span>
        <span className="coding-task__repo">
          {task.repository}
          {task.issueNumber !== undefined && ` #${task.issueNumber}`}
        </span>
      </header>

      <p className="coding-task__title">{task.title}</p>

      {task.errorMessage && <p className="coding-task__error">{task.errorMessage}</p>}

      {task.status === 'COMPLETED' && (
        <p className="coding-task__meta">
          {task.agentStatus && <>Agente: {task.agentStatus}. </>}
          {files.length > 0 && <>{files.length} arquivo(s) alterado(s). </>}
          {task.verification && <>Verificação: {task.verification}.</>}
        </p>
      )}

      <nav className="coding-task__links">
        {task.prUrl && (
          <a className="coding-task__link coding-task__link--primary" href={task.prUrl} target="_blank" rel="noreferrer">
            Ver pull request
          </a>
        )}
        {task.issueUrl && (
          <a className="coding-task__link" href={task.issueUrl} target="_blank" rel="noreferrer">
            Issue
          </a>
        )}
        {task.runUrl && (
          <a className="coding-task__link" href={task.runUrl} target="_blank" rel="noreferrer">
            Execução no Actions
          </a>
        )}
        {task.workBranch && <code className="coding-task__branch">{task.workBranch}</code>}
      </nav>
    </article>
  );
}
