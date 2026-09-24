import { describeChatStatus } from '../utils/statusLabel';

interface StreamingBubbleProps {
  content: string;
  elapsedSeconds: number;
}

/** Bolha da resposta do assistente enquanto ela ainda está sendo transmitida (streaming). */
export function StreamingBubble({ content, elapsedSeconds }: StreamingBubbleProps) {
  const hasContent = content.length > 0;
  const { label } = describeChatStatus('PROCESSING', hasContent, elapsedSeconds);

  return (
    <div className="message-row message-row--start">
      <div className="message-bubble message-bubble--assistant message-bubble--streaming">
        <div className="message-bubble__meta">
          <span className="message-bubble__role">Assistente</span>
          <span className="thinking-tag" role="status">
            <span className="thinking-tag__dot" />
            {label}
          </span>
        </div>
        {hasContent ? (
          <p className="message-bubble__content">
            {content}
            <span className="streaming-cursor" aria-hidden="true" />
          </p>
        ) : (
          <div className="typing-indicator" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        )}
      </div>
    </div>
  );
}
