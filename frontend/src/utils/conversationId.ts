const STORAGE_KEY = 'poc-websocket-demo:conversationId';
const DEFAULT_CONVERSATION_ID = 'conversa-1';

/** Persiste o ID da conversa para que um F5 reconecte à mesma conversa em vez de começar do zero. */
export function loadConversationId(): string {
  try {
    return window.localStorage.getItem(STORAGE_KEY) ?? DEFAULT_CONVERSATION_ID;
  } catch {
    return DEFAULT_CONVERSATION_ID;
  }
}

export function saveConversationId(conversationId: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, conversationId);
  } catch {
    // Armazenamento indisponível (ex.: modo privado) — segue sem persistir.
  }
}
