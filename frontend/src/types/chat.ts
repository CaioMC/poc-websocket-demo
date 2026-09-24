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

/** Mensagens enviadas pelo cliente ao servidor. */
export type OutgoingCommand =
  | { type: 'user_message'; content: string }
  | { type: 'context'; content: string }
  | { type: 'interrupt' }
  | { type: 'reset' };

/** Eventos recebidos do servidor (envelope único discriminado por `type`). */
export type IncomingEvent =
  | { type: 'replay'; status: ChatStatus; messages: ChatMessage[] }
  | { type: 'message_appended'; message: ChatMessage }
  | { type: 'status_changed'; status: ChatStatus }
  | { type: 'reasoning_chunk'; content: string }
  | { type: 'reasoning_completed' }
  | { type: 'reasoning_interrupted' }
  | { type: 'error'; errorMessage: string };

export type ConnectionState = 'connecting' | 'open' | 'closed';
