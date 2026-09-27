# Arquitetura — POC Chat com WebSocket + Spring AI/Ollama

Chat com streaming real de LLM (Ollama, via Spring AI) sobre WebSocket "cru",
organizado em **Ports & Adapters** (Hexagonal): um **core** de domínio que
não conhece infraestrutura, e **adapters** que implementam as portas do core.

## Índice

1. [Por que Ports & Adapters](#1-por-que-ports--adapters)
2. [Estrutura de pacotes](#2-estrutura-de-pacotes)
3. [As portas](#3-as-portas)
4. [Spring AI + Ollama](#4-spring-ai--ollama)
5. [Protocolo WebSocket](#5-protocolo-websocket)
6. [Concorrência: interromper e substituir](#6-concorrência-interromper-e-substituir)
7. [Front-end](#7-front-end)
8. [Testes](#8-testes)

Documentos relacionados: [`websocket-explained.md`](websocket-explained.md)
(guia didático do WebSocket), [`sequence-diagram.md`](sequence-diagram.md)
(fluxos passo a passo) e [`dependencies.md`](dependencies.md) (libs usadas).

## 1. Por que Ports & Adapters

- O **core** não conhece Spring AI, Ollama, WebSocket ou Jackson — só suas
  próprias interfaces (portas).
- Cada porta tem um adapter nesta POC, mas poderia ter outro sem tocar o
  core: trocar Ollama por OpenAI (`ReasoningModelPort`), WebSocket por
  SSE/STOMP, memória por Redis/JPA (`ChatRepository`).
- É o que torna os use cases testáveis com mocks, sem Ollama real nem
  conexão WebSocket (`SendUserMessageServiceTest`, `ChatReasoningRunnerTest`).

## 2. Estrutura de pacotes

```
com.example.wschat/
├── PocWebSocketDemoApplication.java
│
├── core/chat/                              # regra de negócio — zero deps de infra
│   ├── domain/
│   │   ├── Chat, ChatId, ChatMessage, ChatRole, ChatStatus, ChatSnapshot
│   │   ├── repository/ChatRepository.java  # porta de persistência
│   │   ├── ai/ReasoningModelPort.java       # porta do modelo de linguagem
│   │   └── event/ChatEventPublisher.java    # porta de notificação de eventos
│   │
│   ├── usecase/                            # interfaces, uma por caso de uso
│   │   ├── ConnectToChatUseCase, SendUserMessageUseCase
│   │   ├── AddContextUseCase, InterruptReasoningUseCase, ResetChatUseCase
│   │
│   └── application/                        # implementações
│       ├── ConnectToChatService, SendUserMessageService
│       ├── AddContextService, InterruptReasoningService, ResetChatService
│       └── ChatReasoningRunner              # orquestração compartilhada do streaming
│
├── core/codingtask/                        # gatilho do agente de codificação (ver coding-agent.md)
│   ├── domain/                             # CodingTask, CodingTaskStatus, CodingTaskResult
│   │   ├── agent/CodingAgentPort.java       # porta: abrir tarefa, disparar, acompanhar
│   │   ├── repository/CodingTaskRepository.java
│   │   └── event/CodingTaskEventPublisher.java
│   ├── usecase/                            # StartCodingTask, TrackCodingTasks, ListCodingTasks
│   └── application/                        # serviços + CodingTaskChatNotifier (mensagens no chat)
│
├── adapters/codingtask/
│   ├── github/GitHubCodingAgentAdapter      # REST API do GitHub (issue, workflow, artefato)
│   ├── scheduling/CodingTaskProgressPoller  # consulta periódica das tarefas ativas
│   ├── persistence/InMemoryCodingTaskRepository
│   └── config/                              # CodingAgentProperties (app.coding-agent.*)
│
└── adapters/chat/                          # infraestrutura — depende do core, nunca o contrário
    ├── websocket/
    │   ├── ChatWebSocketConfig, ChatWebSocketHandler
    │   ├── session/ChatSessionRegistry      # sessões abertas por ChatId
    │   ├── event/ChatWebSocketEventBroadcaster
    │   └── dto/                             # IncomingChatMessage, OutgoingChatEvent, ChatMessageView, CodingTaskView
    ├── ai/OllamaReasoningModelAdapter.java  # implementa ReasoningModelPort
    └── persistence/InMemoryChatRepository.java

frontend/                                   # UI React (seção 7)
```

Fluxo de dependência, sem ciclos: `adapters → core.usecase → core.application → core.domain`.

## 3. As portas

| Porta | Papel | Adapter (nesta POC) |
|---|---|---|
| `ChatRepository` | Persistir/recuperar o estado de um chat | `InMemoryChatRepository` |
| `ReasoningModelPort` | Resposta do modelo, em streaming | `OllamaReasoningModelAdapter` (Spring AI) |
| `ChatEventPublisher` | Notificar mudanças/mensagens ao mundo externo | `ChatWebSocketEventBroadcaster` |
| `CodingAgentPort` | Abrir a tarefa, disparar e acompanhar o agente de codificação | `GitHubCodingAgentAdapter` (issue + GitHub Actions) |
| `CodingTaskRepository` | Persistir as tarefas de codificação | `InMemoryCodingTaskRepository` |
| `CodingTaskEventPublisher` | Avisar a interface que uma tarefa mudou | `ChatWebSocketEventBroadcaster` (o mesmo adapter do chat) |

As três últimas portas pertencem ao contexto `codingtask`, o gatilho do agente de
codificação. O desenho completo, com diagramas, está em
[`coding-agent.md`](coding-agent.md).

## 4. Spring AI + Ollama

- `OllamaReasoningModelAdapter` usa `ChatClient` sobre `OllamaChatModel`,
  autoconfigurado a partir de `spring.ai.ollama.*` (`application.yaml`).
- `streamReply(history)` retorna `Flux<String>`; `ChatReasoningRunner`
  assina esse `Flux` em `Schedulers.boundedElastic()` e publica cada chunk.
- **"Preciso de mais contexto"**: heurística de prompt (sem function
  calling) — o system prompt instrui o modelo a iniciar a resposta com
  `ChatReasoningRunner.NEEDS_CONTEXT_PREFIX` quando precisa de um dado
  específico. A constante é compartilhada entre o adapter de IA (que
  instrui o modelo) e o core (que decide o próximo `ChatStatus`).
- `OLLAMA_BASE_URL` e `OLLAMA_CHAT_MODEL` configuráveis por variável de
  ambiente, sem recompilar.

## 5. Protocolo WebSocket

Endpoint: `ws://<host>:3000/ws/chat?conversationId=<id>`. Ver
[`websocket-explained.md`](websocket-explained.md#4-o-protocolo-um-envelope-um-campo-type)
para a explicação completa; resumo dos tipos de mensagem:

**Cliente → servidor**: `user_message`, `context`, `interrupt`, `reset`,
`coding_task` (pedido ao agente de codificação, enviado pelo comando `/codificar`).

**Servidor → cliente**: `replay`, `message_appended`, `status_changed`,
`reasoning_chunk`, `reasoning_completed`, `reasoning_interrupted`, `error`,
`coding_task_updated` (estado completo de uma tarefa do agente; também enviado
logo depois do `replay`, ao conectar).

`reasoning_completed` é sempre o último evento de uma rodada bem-sucedida —
o cliente pode parar de escutar com segurança ao recebê-lo.

## 6. Concorrência: interromper e substituir

- `Chat` é thread-safe (`CopyOnWriteArrayList` + `volatile status`): acessado
  pela thread do WebSocket e pela thread do `Schedulers.boundedElastic()`.
- `ChatReasoningRunner` é um bean único que guarda a rodada ativa por
  conversa (`Map<ChatId, ActiveReasoning>`) — é o que permite:
  - **Parar** (`InterruptReasoningUseCase`): cancela o `Flux` e preserva o
    trecho já gerado no histórico, marcado `interrupted`.
  - **Substituir**: mandar uma nova mensagem enquanto o modelo ainda
    responde interrompe a rodada atual (mesmo efeito de parar) e já inicia
    a próxima com a nova pergunta — como ChatGPT/Claude.
- O processamento continua em background mesmo se o cliente desconectar; ao
  reconectar, `ConnectToChatUseCase` devolve o estado mais atual.

Detalhes de implementação (CAS, `Disposable.dispose()`, por que
`StringBuffer`): ver comentários em `ChatReasoningRunner.java`.

## 7. Front-end

React 18 + TypeScript + Vite (`frontend/`), tema azul/branco.

```
frontend/src/
├── main.tsx                  # entry point
├── App.tsx                   # composição da tela
├── index.css                 # tema azul/branco
├── types/chat.ts             # espelho tipado do protocolo WS do backend
├── hooks/
│   ├── useChatSocket.ts      # transporte: WebSocket + reconexão com backoff
│   ├── useChat.ts            # estado da conversa (mensagens, status, streaming)
│   └── useElapsedSeconds.ts  # cronômetro do "pensando há Ns"
├── components/
│   ├── ChatHeader.tsx        # ID da conversa, status do modelo, reiniciar
│   ├── MessageList.tsx       # histórico + auto-scroll
│   ├── MessageBubble.tsx     # bolha por papel (usuário/assistente/contexto)
│   ├── StreamingBubble.tsx   # bolha em streaming + indicador de status
│   ├── Composer.tsx          # input + botão enviar/parar
│   └── ErrorBanner.tsx
└── utils/
    ├── conversationId.ts     # persiste o ID da conversa no localStorage
    └── statusLabel.ts        # traduz status em rótulo (header + streaming)
```

Consome o protocolo da seção 5 via duas camadas: `useChatSocket` cuida só
do transporte; `useChat` traduz eventos em estado de UI. Nenhuma mensagem é
otimista — a única fonte de verdade é o servidor.

`npm run build` gera os assets em `target/classes/static`, de onde o Spring
Boot os serve — nenhum servidor HTTP adicional.

## 8. Testes

- **Unitários** (`*Test`, JUnit 5 + Mockito + Awaitility): core com portas
  mockadas — sem Spring context, WebSocket ou Ollama real.
- **Integração** (`*IT`, `@SpringBootTest` + `maven-failsafe-plugin`):
  Spring context completo, `ReasoningModelPort` **falso** e determinístico
  (`@TestConfiguration`/`@Primary`), conecta via `StandardWebSocketClient`.
- `mvn verify` roda os dois (Surefire + Failsafe).
