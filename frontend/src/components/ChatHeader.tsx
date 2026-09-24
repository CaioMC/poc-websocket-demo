import { useState } from 'react';
import type { ChatStatus, ConnectionState } from '../types/chat';
import { describeChatStatus } from '../utils/statusLabel';

interface ChatHeaderProps {
  conversationId: string;
  onChangeConversationId: (conversationId: string) => void;
  connectionState: ConnectionState;
  status: ChatStatus;
  hasStreamingContent: boolean;
  elapsedSeconds: number;
  onReset: () => void;
}

const CONNECTION_LABEL: Record<ConnectionState, string> = {
  connecting: 'Conectando ao servidor…',
  open: 'Conectado ao servidor',
  closed: 'Desconectado do servidor',
};

export function ChatHeader({
  conversationId,
  onChangeConversationId,
  connectionState,
  status,
  hasStreamingContent,
  elapsedSeconds,
  onReset,
}: ChatHeaderProps) {
  const [editing, setEditing] = useState(false);
  const [draftId, setDraftId] = useState(conversationId);

  const commitConversationId = () => {
    const trimmed = draftId.trim();
    setEditing(false);
    if (trimmed && trimmed !== conversationId) {
      onChangeConversationId(trimmed);
    } else {
      setDraftId(conversationId);
    }
  };

  const modelStatus = describeChatStatus(status, hasStreamingContent, elapsedSeconds);

  return (
    <header className="chat-header">
      <div className="chat-header__brand">
        <span className="chat-header__logo" aria-hidden="true">
          ✦
        </span>
        <div>
          <h1 className="chat-header__title">Assistente de Chat</h1>
          {editing ? (
            <input
              className="chat-header__id-input"
              value={draftId}
              autoFocus
              onChange={(event) => setDraftId(event.target.value)}
              onBlur={commitConversationId}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.currentTarget.blur();
                } else if (event.key === 'Escape') {
                  setDraftId(conversationId);
                  setEditing(false);
                }
              }}
            />
          ) : (
            <button type="button" className="chat-header__id-button" onClick={() => setEditing(true)} title="Alterar ID da conversa">
              conversa: {conversationId}
              <span className="chat-header__id-edit-icon" aria-hidden="true">
                ✎
              </span>
            </button>
          )}
        </div>
      </div>

      <div className="chat-header__status">
        <span
          className={`connection-dot connection-dot--${connectionState}`}
          title={CONNECTION_LABEL[connectionState]}
          aria-label={CONNECTION_LABEL[connectionState]}
        />

        <span className={`model-status model-status--${modelStatus.tone}`} role="status">
          <ModelStatusIcon tone={modelStatus.tone} />
          {modelStatus.label}
        </span>

        <button type="button" className="chat-header__reset" onClick={onReset} title="Reiniciar esta conversa">
          Reiniciar
        </button>
      </div>
    </header>
  );
}

function ModelStatusIcon({ tone }: { tone: string }) {
  if (tone === 'processing') {
    return <span className="model-status__spinner" aria-hidden="true" />;
  }
  const glyph = { idle: '●', waiting: '⏳', error: '⚠' }[tone] ?? '●';
  return (
    <span className="model-status__glyph" aria-hidden="true">
      {glyph}
    </span>
  );
}
