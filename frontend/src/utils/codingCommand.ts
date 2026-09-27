/** Comando digitado no chat que aciona o agente de codificação. */
export const CODING_COMMAND = '/codificar';

/**
 * Se o texto for um comando `/codificar ...`, devolve o pedido (o que vem depois do comando).
 * Caso contrário, devolve `null` e a mensagem segue como conversa normal com o LLM.
 */
export function parseCodingCommand(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed !== CODING_COMMAND && !trimmed.startsWith(`${CODING_COMMAND} `) && !trimmed.startsWith(`${CODING_COMMAND}\n`)) {
    return null;
  }
  return trimmed.slice(CODING_COMMAND.length).trim();
}
