package com.example.wschat.core.chat.domain;

/** Status corrente do processamento de uma conversa. */
public enum ChatStatus {
    IDLE,
    PROCESSING,
    WAITING_FOR_CONTEXT,
    ERROR
}
