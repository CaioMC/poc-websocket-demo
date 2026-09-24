import type { ChatMessage } from '../types/chat';

const ROLE_LABEL: Record<ChatMessage['role'], string> = {
  SYSTEM: 'Sistema',
  USER: 'Você',
  CONTEXT: 'Contexto adicional',
  ASSISTANT: 'Assistente',
};

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

export function MessageBubble({ message }: { message: ChatMessage }) {
  const alignment = message.role === 'USER' ? 'end' : 'start';

  return (
    <div className={`message-row message-row--${alignment}`}>
      <div className={`message-bubble message-bubble--${message.role.toLowerCase()}`}>
        <div className="message-bubble__meta">
          <span className="message-bubble__role">{ROLE_LABEL[message.role]}</span>
          <span className="message-bubble__time">{formatTime(message.createdAt)}</span>
        </div>
        <p className="message-bubble__content">{message.content}</p>
        {message.interrupted && (
          <div className="message-bubble__interrupted">
            <span aria-hidden="true">■</span> resposta interrompida
          </div>
        )}
      </div>
    </div>
  );
}
