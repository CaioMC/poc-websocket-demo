import { useRef, useState } from 'react';
import type { ChangeEvent, FormEvent, KeyboardEvent } from 'react';
import type { ChatStatus, ConnectionState } from '../types/chat';
import { CODING_COMMAND, parseCodingCommand } from '../utils/codingCommand';

interface ComposerProps {
  status: ChatStatus;
  connectionState: ConnectionState;
  onSendMessage: (content: string) => void;
  onSendContext: (content: string) => void;
  onStartCodingTask: (request: string) => void;
  onInterrupt: () => void;
}

const MAX_TEXTAREA_HEIGHT_PX = 160;

export function Composer({ status, connectionState, onSendMessage, onSendContext, onStartCodingTask, onInterrupt }: ComposerProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const isWaitingForContext = status === 'WAITING_FOR_CONTEXT';
  const isProcessing = status === 'PROCESSING';
  const canConnect = connectionState === 'open';
  const hasText = value.trim().length > 0;
  const codingRequest = parseCodingCommand(value);
  const isCodingCommand = codingRequest !== null;

  // Vira "parar" enquanto processa; digitar volta a mostrar "enviar" (interrompe e substitui).
  const showStopButton = isProcessing && !hasText;
  // "/codificar" sozinho não basta: é preciso dizer o que implementar.
  const canSubmit = hasText && (!isCodingCommand || codingRequest.length > 0);

  const resizeTextarea = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, MAX_TEXTAREA_HEIGHT_PX)}px`;
  };

  const handleChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setValue(event.target.value);
    resizeTextarea();
  };

  const submit = () => {
    const trimmed = value.trim();
    if (!trimmed || !canConnect || !canSubmit) return;
    if (codingRequest) {
      // Não vai para o LLM do chat: vira uma issue + workflow do agente de codificação.
      onStartCodingTask(codingRequest);
    } else if (isWaitingForContext) {
      onSendContext(trimmed);
    } else {
      onSendMessage(trimmed);
    }
    setValue('');
    requestAnimationFrame(resizeTextarea);
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (showStopButton) {
      onInterrupt();
      return;
    }
    submit();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      if (canSubmit) {
        submit();
      }
    }
  };

  return (
    <form className="composer" onSubmit={handleSubmit}>
      {isWaitingForContext && (
        <div className="composer__banner composer__banner--context">
          O assistente precisa de mais informações para continuar. Envie o contexto abaixo.
        </div>
      )}

      <div className="composer__row">
        <textarea
          ref={textareaRef}
          className="composer__input"
          placeholder={
            !canConnect
              ? 'Conectando ao servidor…'
              : isWaitingForContext
                ? 'Digite o contexto solicitado…'
                : `Envie uma mensagem ou ${CODING_COMMAND} <o que implementar>…`
          }
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          disabled={!canConnect}
          rows={1}
        />
        <button
          type="submit"
          className={`composer__submit ${showStopButton ? 'composer__submit--stop' : ''}`}
          disabled={!canConnect || (!showStopButton && !canSubmit)}
          aria-label={showStopButton ? 'Parar geração' : 'Enviar mensagem'}
          title={showStopButton ? 'Parar geração' : 'Enviar mensagem'}
        >
          {showStopButton ? <StopIcon /> : <SendIcon />}
        </button>
      </div>

      {isCodingCommand ? (
        <p className="composer__hint composer__hint--coding">
          {codingRequest
            ? 'Vai abrir uma issue no GitHub com este pedido e o contexto da conversa, e disparar o agente de codificação.'
            : `Descreva a tarefa depois de ${CODING_COMMAND}. Ex.: ${CODING_COMMAND} Criar endpoint GET /api/health`}
        </p>
      ) : (
        isProcessing &&
        hasText && <p className="composer__hint">Enviar agora interrompe a resposta em andamento e inicia uma nova.</p>
      )}
    </form>
  );
}

function SendIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3.4 20.6 21 12 3.4 3.4 3 10l12 2-12 2z" fill="currentColor" />
    </svg>
  );
}

function StopIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="4" y="4" width="16" height="16" rx="2" fill="currentColor" />
    </svg>
  );
}
