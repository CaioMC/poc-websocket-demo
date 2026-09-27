/**
 * Contratos do protocolo WebSocket do chat — espelho tipado dos DTOs do
 * backend (`IncomingChatMessage` / `OutgoingChatEvent`).
 */

export type ChatRole = 'SYSTEM' | 'USER' | 'CONTEXT' | 'ASSISTANT';

export type ChatStatus = 'IDLE' | 'PROCESSING' | 'WAITING_FOR_CONTEXT' | 'ERROR';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  createdAt: string;
  /** Resposta que o usuário interrompeu no meio do streaming. */
  interrupted?: boolean;
}

/** Ciclo de vida de uma tarefa do agente de codificação (espelho de `CodingTaskStatus`). */
export type CodingTaskStatus = 'REQUESTED' | 'ISSUE_OPENED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/** Cartão de uma tarefa do agente de codificação (espelho de `CodingTaskView`). */
export interface CodingTask {
  id: string;
  status: CodingTaskStatus;
  repository: string;
  title: string;
  issueNumber?: number;
  issueUrl?: string;
  workBranch?: string;
  runUrl?: string;
  prUrl?: string;
  /** Status do próprio agente, lido do result.json: COMPLETED, VERIFICATION_FAILED, INCOMPLETE... */
  agentStatus?: string;
  summary?: string;
  changedFiles?: string[];
  verification?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

/** Mensagens enviadas pelo cliente ao servidor. */
export type OutgoingCommand =
  | { type: 'user_message'; content: string }
  | { type: 'context'; content: string }
  | { type: 'interrupt' }
  | { type: 'reset' }
  | { type: 'coding_task'; content: string };

/** Eventos recebidos do servidor (envelope único discriminado por `type`). */
export type IncomingEvent =
  | { type: 'replay'; status: ChatStatus; messages: ChatMessage[] }
  | { type: 'message_appended'; message: ChatMessage }
  | { type: 'status_changed'; status: ChatStatus }
  | { type: 'reasoning_chunk'; content: string }
  | { type: 'reasoning_completed' }
  | { type: 'reasoning_interrupted' }
  | { type: 'error'; errorMessage: string }
  | { type: 'coding_task_updated'; codingTask: CodingTask };

export type ConnectionState = 'connecting' | 'open' | 'closed';
