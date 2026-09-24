# Diagramas de Sequência

Seis fluxos, cada um em seu próprio diagrama — mais fácil de seguir do que
um único diagrama gigante. Todos cobertos por `ChatWebSocketHandlerIT`
(exceto o de reconexão de rede no cliente, coberto pelo backoff de
`useChatSocket.ts`).

## Índice

1. [Conectar / reconectar (replay)](#1-conectar--reconectar-replay)
2. [Enviar mensagem → streaming completo](#2-enviar-mensagem--streaming-completo)
3. [Parar a geração (stop)](#3-parar-a-geração-stop)
4. [Nova mensagem durante o processamento](#4-nova-mensagem-durante-o-processamento)
5. [Contexto adicional (retomada)](#5-contexto-adicional-retomada)
6. [Reset](#6-reset)

Componentes: **FE** (React), **H** `ChatWebSocketHandler`, **REG**
`ChatSessionRegistry`, os *use cases* (**CONNECT**, **SEND**, **CTX**,
**INTERRUPT**, **RESET**), **RUN** `ChatReasoningRunner`, **AI**
`OllamaReasoningModelAdapter`, **BC** `ChatWebSocketEventBroadcaster`,
**REPO** `InMemoryChatRepository`.

---

## 1. Conectar / reconectar (replay)

Sem polling: o servidor manda o histórico + status assim que a conexão abre
— inclusive o que foi processado enquanto o cliente estava offline.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant REG as ChatSessionRegistry
    participant CONNECT as ConnectToChatUseCase
    participant REPO as InMemoryChatRepository
    participant BC as ChatWebSocketEventBroadcaster

    U->>FE: abre a página
    FE->>H: WS connect ?conversationId=X
    activate H
    H->>REG: register(chatId, session)
    H->>CONNECT: connect(chatId)
    CONNECT->>REPO: findOrCreate(chatId)
    REPO-->>CONNECT: Chat (status + histórico)
    CONNECT-->>H: ChatSnapshot
    H->>BC: sendReplayTo(session, snapshot)
    BC-->>FE: {"type":"replay", status, messages[]}
    deactivate H
    FE-->>U: histórico e status renderizados
```

## 2. Enviar mensagem → streaming completo

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant SEND as SendUserMessageUseCase
    participant RUN as ChatReasoningRunner
    participant AI as OllamaReasoningModelAdapter
    participant OLLAMA as Ollama (LLM)
    participant BC as ChatWebSocketEventBroadcaster

    U->>FE: digita e envia
    FE->>H: {"type":"user_message", content}
    H->>SEND: send(chatId, content)
    SEND->>BC: publishMessageAppended(USER)
    BC-->>FE: message_appended
    SEND->>RUN: runAsync(chat)
    RUN->>BC: publishStatusChanged(PROCESSING)
    BC-->>FE: status_changed
    RUN->>AI: streamReply(history)
    AI->>OLLAMA: POST /api/chat (stream)
    loop cada chunk
        OLLAMA-->>AI: chunk
        AI-->>RUN: onNext(chunk)
        RUN->>BC: publishReasoningChunk(chunk)
        BC-->>FE: reasoning_chunk
        FE-->>U: texto cresce incrementalmente
    end
    OLLAMA-->>AI: fim do streaming
    RUN->>BC: publishMessageAppended(ASSISTANT)
    BC-->>FE: message_appended
    RUN->>BC: publishStatusChanged(IDLE | WAITING_FOR_CONTEXT)
    BC-->>FE: status_changed
    RUN->>BC: publishReasoningCompleted()
    BC-->>FE: reasoning_completed
```

## 3. Parar a geração (stop)

Botão "parar" do composer — clicado enquanto o assistente ainda responde.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant INTERRUPT as InterruptReasoningUseCase
    participant RUN as ChatReasoningRunner
    participant BC as ChatWebSocketEventBroadcaster

    Note over RUN: rodada em andamento, buffer parcial acumulado
    U->>FE: clica "parar"
    FE->>H: {"type":"interrupt"}
    H->>INTERRUPT: interrupt(chatId)
    INTERRUPT->>RUN: interrupt(chatId)
    RUN->>RUN: dispose() da subscrição do Flux
    RUN->>BC: publishMessageAppended(ASSISTANT, interrupted=true)
    BC-->>FE: message_appended (parcial preservado)
    RUN->>BC: publishStatusChanged(IDLE)
    BC-->>FE: status_changed
    RUN->>BC: publishReasoningInterrupted()
    BC-->>FE: reasoning_interrupted
    FE-->>U: bolha marcada "resposta interrompida"
```

## 4. Nova mensagem durante o processamento

Sem clicar em "parar" antes — mandar outra mensagem já interrompe a rodada
atual e substitui pela nova, como no ChatGPT/Claude.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant SEND as SendUserMessageUseCase
    participant RUN as ChatReasoningRunner
    participant BC as ChatWebSocketEventBroadcaster

    Note over RUN: 1ª rodada em andamento, buffer parcial acumulado
    U->>FE: digita nova mensagem e envia
    FE->>H: {"type":"user_message", content}
    H->>SEND: send(chatId, content)
    SEND->>RUN: interrupt(chatId)
    RUN->>BC: publishMessageAppended(ASSISTANT, interrupted=true)
    BC-->>FE: message_appended (1ª resposta parcial preservada)
    RUN->>BC: publishStatusChanged(IDLE) + publishReasoningInterrupted()
    BC-->>FE: status_changed / reasoning_interrupted
    SEND->>BC: publishMessageAppended(USER, nova mensagem)
    BC-->>FE: message_appended
    SEND->>RUN: runAsync(chat)
    Note over RUN: segue o mesmo fluxo do diagrama 2
```

## 5. Contexto adicional (retomada)

Quando o modelo pede mais informação (`WAITING_FOR_CONTEXT`), o usuário
responde pela mesma conexão e o raciocínio retoma automaticamente.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant CTX as AddContextUseCase
    participant RUN as ChatReasoningRunner
    participant BC as ChatWebSocketEventBroadcaster

    Note over FE: status atual = WAITING_FOR_CONTEXT
    U->>FE: preenche o contexto pedido
    FE->>H: {"type":"context", content}
    H->>CTX: addContext(chatId, content)
    CTX->>BC: publishMessageAppended(CONTEXT)
    BC-->>FE: message_appended
    alt status era WAITING_FOR_CONTEXT
        CTX->>RUN: runAsync(chat)
        Note over RUN: mesmo fluxo de streaming do diagrama 2
    end
```

## 6. Reset

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as React app
    participant H as ChatWebSocketHandler
    participant RESET as ResetChatUseCase
    participant RUN as ChatReasoningRunner
    participant REPO as InMemoryChatRepository
    participant BC as ChatWebSocketEventBroadcaster

    U->>FE: clica "Reiniciar"
    FE->>H: {"type":"reset"}
    H->>RESET: reset(chatId)
    RESET->>RUN: discardActiveReasoning(chatId)
    Note over RUN: cancela sem preservar parcial nem publicar eventos
    RESET->>REPO: reset(chatId)
    RESET->>BC: publishStatusChanged(IDLE)
    BC-->>FE: status_changed
    FE-->>U: histórico local limpo
```

## Observações

- O streaming continua em background mesmo se o cliente desconectar — o
  `Flux` não depende de nenhuma `WebSocketSession` (ver
  [`websocket-explained.md`](websocket-explained.md#6-estado-no-servidor-não-na-conexão)).
- `ChatReasoningRunner` é o único ponto que sabe interromper uma rodada —
  reutilizado por `InterruptReasoningService` (diagrama 3) e
  `SendUserMessageService` (diagrama 4); "substituir" não é um caso
  especial, é `interrupt()` seguido de `runAsync()`.
- `ChatWebSocketEventBroadcaster` é o único ponto que serializa para JSON e
  escreve nas sessões WebSocket ativas — o `core` não depende disso.
