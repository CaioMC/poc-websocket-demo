package com.example.wschat.core.chat.domain;

/** Papel de quem enviou uma {@link ChatMessage} na conversa. */
public enum ChatRole {
    SYSTEM,
    USER,
    /** Complemento enviado pelo usuário na mesma conexão; tratado como entrada do usuário pelo LLM. */
    CONTEXT,
    ASSISTANT
}
