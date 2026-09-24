import { useEffect, useRef } from 'react';
import type { ChatMessage, ChatStatus } from '../types/chat';
import { MessageBubble } from './MessageBubble';
import { StreamingBubble } from './StreamingBubble';

interface MessageListProps {
  messages: ChatMessage[];
  streamingContent: string | null;
  status: ChatStatus;
  elapsedSeconds: number;
}

export function MessageList({ messages, streamingContent, status, elapsedSeconds }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const isEmpty = messages.length === 0 && streamingContent === null;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages.length, streamingContent]);

  if (isEmpty) {
    return (
      <div className="message-list message-list--empty">
        <div className="empty-state">
          <span className="empty-state__icon" aria-hidden="true">
            💬
          </span>
          <h2>Comece uma conversa</h2>
          <p>Pergunte algo como “Qual a validade de uma banana média?” e acompanhe a resposta chegando em tempo real.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="message-list">
      {messages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      {status === 'PROCESSING' && <StreamingBubble content={streamingContent ?? ''} elapsedSeconds={elapsedSeconds} />}
      <div ref={bottomRef} />
    </div>
  );
}
