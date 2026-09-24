import { useCallback, useEffect, useRef, useState } from 'react';
import type { ConnectionState, IncomingEvent, OutgoingCommand } from '../types/chat';

interface UseChatSocketOptions {
  conversationId: string;
  onEvent: (event: IncomingEvent) => void;
}

interface UseChatSocketResult {
  connectionState: ConnectionState;
  send: (command: OutgoingCommand) => void;
}

const RECONNECT_BASE_DELAY_MS = 500;
const RECONNECT_MAX_DELAY_MS = 8_000;

function buildSocketUrl(conversationId: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/chat?conversationId=${encodeURIComponent(conversationId)}`;
}

/** Ciclo de vida da conexão WebSocket: abertura, entrega de eventos, envio de comandos, reconexão com backoff. */
export function useChatSocket({ conversationId, onEvent }: UseChatSocketOptions): UseChatSocketResult {
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting');
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const onEventRef = useRef(onEvent);

  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    let disposed = false;

    const clearReconnectTimeout = () => {
      if (reconnectTimeoutRef.current !== null) {
        window.clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };

    const scheduleReconnect = () => {
      const attempt = reconnectAttemptsRef.current;
      const delay = Math.min(RECONNECT_BASE_DELAY_MS * 2 ** attempt, RECONNECT_MAX_DELAY_MS);
      reconnectAttemptsRef.current = attempt + 1;
      reconnectTimeoutRef.current = window.setTimeout(connect, delay);
    };

    function connect() {
      if (disposed) {
        return;
      }

      setConnectionState('connecting');
      const socket = new WebSocket(buildSocketUrl(conversationId));
      socketRef.current = socket;

      socket.onopen = () => {
        reconnectAttemptsRef.current = 0;
        setConnectionState('open');
      };

      socket.onmessage = (event) => {
        try {
          onEventRef.current(JSON.parse(event.data as string) as IncomingEvent);
        } catch {
          // Payload inesperado: ignorar em vez de derrubar a UI inteira.
        }
      };

      socket.onclose = () => {
        socketRef.current = null;
        setConnectionState('closed');
        if (!disposed) {
          scheduleReconnect();
        }
      };

      socket.onerror = () => socket.close();
    }

    connect();

    return () => {
      disposed = true;
      clearReconnectTimeout();
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, [conversationId]);

  const send = useCallback((command: OutgoingCommand) => {
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(command));
    }
  }, []);

  return { connectionState, send };
}
