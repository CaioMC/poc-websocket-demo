import { useCallback, useRef, useState } from 'react';
import { useChatSocket } from './useChatSocket';
import type { ChatMessage, ChatStatus, ConnectionState, IncomingEvent } from '../types/chat';

export interface UseChatResult {
  connectionState: ConnectionState;
  status: ChatStatus;
  messages: ChatMessage[];
  /** Texto acumulado da resposta em streaming; `null` quando não há rodada em andamento. */
  streamingContent: string | null;
  errorMessage: string | null;
  sendMessage: (content: string) => void;
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

  const interrupt = useCallback(() => send({ type: 'interrupt' }), [send]);

  const reset = useCallback(() => {
    knownIds.current = new Set();
    setMessages([]);
    setStreamingContent(null);
    setErrorMessage(null);
    setStatus(INITIAL_STATUS);
    send({ type: 'reset' });
  }, [send]);

  return {
    connectionState,
    status,
    messages,
    streamingContent,
    errorMessage,
    sendMessage,
    sendContext,
    interrupt,
    reset,
  };
}
