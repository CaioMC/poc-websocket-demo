import { useCallback, useRef, useState } from 'react';
import { useChatSocket } from './useChatSocket';
import type { ChatMessage, ChatStatus, CodingTask, ConnectionState, IncomingEvent } from '../types/chat';

export interface UseChatResult {
  connectionState: ConnectionState;
  status: ChatStatus;
  messages: ChatMessage[];
  /** Texto acumulado da resposta em streaming; `null` quando não há rodada em andamento. */
  streamingContent: string | null;
  errorMessage: string | null;
  /** Tarefas do agente de codificação desta conversa, da mais recente para a mais antiga. */
  codingTasks: CodingTask[];
  sendMessage: (content: string) => void;
  /** Envia um pedido ao agente de codificação (o texto depois de `/codificar`). */
  startCodingTask: (request: string) => void;
  sendContext: (content: string) => void;
  interrupt: () => void;
  reset: () => void;
}

const INITIAL_STATUS: ChatStatus = 'IDLE';

/** Estado do chat derivado só dos eventos do servidor — sem mensagens otimistas no cliente. */
export function useChat(conversationId: string): UseChatResult {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<ChatStatus>(INITIAL_STATUS);
  const [streamingContent, setStreamingContent] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [codingTasks, setCodingTasks] = useState<CodingTask[]>([]);

  // Evita duplicar mensagens caso um `message_appended` chegue antes do reconhecimento
  // de uma reconexão, ou em qualquer outra sobreposição entre replay e eventos ao vivo.
  const knownIds = useRef<Set<string>>(new Set());

  const onEvent = useCallback((event: IncomingEvent) => {
    switch (event.type) {
      case 'replay': {
        knownIds.current = new Set(event.messages.map((message) => message.id));
        setMessages(event.messages);
        setStatus(event.status);
        setStreamingContent(null);
        setErrorMessage(null);
        // O servidor reenvia o estado de cada tarefa logo depois do replay.
        setCodingTasks([]);
        break;
      }
      case 'message_appended': {
        if (knownIds.current.has(event.message.id)) {
          break;
        }
        knownIds.current.add(event.message.id);
        setMessages((prev) => [...prev, event.message]);
        if (event.message.role === 'ASSISTANT') {
          setStreamingContent(null);
        }
        break;
      }
      case 'status_changed': {
        setStatus(event.status);
        if (event.status === 'PROCESSING') {
          setErrorMessage(null);
        }
        break;
      }
      case 'reasoning_chunk': {
        setStreamingContent((prev) => (prev ?? '') + event.content);
        break;
      }
      case 'reasoning_completed':
      case 'reasoning_interrupted': {
        setStreamingContent(null);
        break;
      }
      case 'error': {
        setErrorMessage(event.errorMessage);
        break;
      }
      case 'coding_task_updated': {
        // Upsert: cada evento traz o estado completo da tarefa, então basta substituir.
        const task = event.codingTask;
        setCodingTasks((prev) =>
          [task, ...prev.filter((existing) => existing.id !== task.id)].sort((a, b) =>
            b.createdAt.localeCompare(a.createdAt),
          ),
        );
        break;
      }
    }
  }, []);

  const { connectionState, send } = useChatSocket({ conversationId, onEvent });

  const sendMessage = useCallback(
    (content: string) => {
      const trimmed = content.trim();
      if (!trimmed) {
        return;
      }
      send({ type: 'user_message', content: trimmed });
    },
    [send],
  );

  const sendContext = useCallback(
    (content: string) => {
      const trimmed = content.trim();
      if (!trimmed) {
        return;
      }
      send({ type: 'context', content: trimmed });
    },
    [send],
  );

  const startCodingTask = useCallback(
    (request: string) => {
      send({ type: 'coding_task', content: request.trim() });
    },
    [send],
  );

  const interrupt = useCallback(() => send({ type: 'interrupt' }), [send]);

  const reset = useCallback(() => {
    knownIds.current = new Set();
    setMessages([]);
    setStreamingContent(null);
    setErrorMessage(null);
    setCodingTasks([]);
    setStatus(INITIAL_STATUS);
    send({ type: 'reset' });
  }, [send]);

  return {
    connectionState,
    status,
    messages,
    streamingContent,
    errorMessage,
    codingTasks,
    sendMessage,
    startCodingTask,
    sendContext,
    interrupt,
    reset,
  };
}
