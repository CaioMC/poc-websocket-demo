import type { ChatStatus } from '../types/chat';

export type StatusTone = 'idle' | 'processing' | 'waiting' | 'error';

export interface StatusDescriptor {
  label: string;
  tone: StatusTone;
}

/** Traduz status + streaming + tempo decorrido num rótulo único, usado no header e na bolha de streaming. */
export function describeChatStatus(status: ChatStatus, hasStreamingContent: boolean, elapsedSeconds: number): StatusDescriptor {
  switch (status) {
    case 'IDLE':
      return { label: 'Pronto', tone: 'idle' };
    case 'WAITING_FOR_CONTEXT':
      return { label: 'Aguardando contexto', tone: 'waiting' };
    case 'ERROR':
      return { label: 'Erro ao processar', tone: 'error' };
    case 'PROCESSING': {
      const verb = hasStreamingContent ? 'Respondendo' : 'Pensando';
      const label = elapsedSeconds > 0 ? `${verb} há ${elapsedSeconds}s` : `${verb}…`;
      return { label, tone: 'processing' };
    }
  }
}
