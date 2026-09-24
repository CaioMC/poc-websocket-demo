# WebSocket neste projeto — guia didático

Um passeio pelo "por quê" de cada decisão, usando este projeto como exemplo
prático. Não é referência de API — para isso veja [`architecture.md`](architecture.md)
e [`sequence-diagram.md`](sequence-diagram.md).

## Índice

1. [O problema: por que não HTTP comum](#1-o-problema-por-que-não-http-comum)
2. [WebSocket "cru" vs STOMP](#2-websocket-cru-vs-stomp)
3. [Anatomia da conexão](#3-anatomia-da-conexão)
4. [O protocolo: um envelope, um campo `type`](#4-o-protocolo-um-envelope-um-campo-type)
5. [Streaming: a resposta chegando em pedaços](#5-streaming-a-resposta-chegando-em-pedaços)
6. [Estado no servidor, não na conexão](#6-estado-no-servidor-não-na-conexão)
7. [Parar e substituir: o "stop" por baixo dos panos](#7-parar-e-substituir-o-stop-por-baixo-dos-panos)
8. [Reconexão no cliente](#8-reconexão-no-cliente)
9. [Testando WebSocket sem um servidor real](#9-testando-websocket-sem-um-servidor-real)

---

## 1. O problema: por que não HTTP comum

Um chat com um LLM tem duas características que o request/response clássico
do HTTP não resolve bem:

- **A resposta chega aos poucos** (streaming). Com HTTP puro você esperaria
  a resposta inteira pronta, ou recorreria a Server-Sent Events (SSE) — que
  só funciona em uma direção (servidor → cliente).
- **O servidor também precisa falar primeiro**: quando o usuário reconecta,
  o servidor manda o histórico sem o cliente precisar perguntar (sem
  polling). Isso é comunicação bidirecional de verdade.

WebSocket resolve os dois: depois de um handshake HTTP inicial, a conexão
vira um canal full-duplex — qualquer lado manda uma mensagem a qualquer
momento, sem reabrir conexão.

## 2. WebSocket "cru" vs STOMP

Spring oferece duas camadas para WebSocket:

| Abordagem | O que dá | Quando vale a pena |
|---|---|---|
| **`TextWebSocketHandler`** (usado aqui) | Você define o protocolo (formato das mensagens) do zero. | Protocolo simples, poucos tipos de mensagem, sem necessidade de tópicos/broadcast complexo. |
| **STOMP** (`@MessageMapping`, brokers) | Roteamento por destino (`/topic/...`), integração com brokers de mensageria. | Múltiplos canais/tópicos, apps maiores, várias features de pub/sub. |

Aqui só existe **um** tipo de canal (a conversa) e **poucos** tipos de
mensagem — STOMP adicionaria camadas (subscriptions, destinos) sem
necessidade. `ChatWebSocketHandler` extends `TextWebSocketHandler` e resolve
tudo com um `switch` sobre um campo `type` (seção 4).

## 3. Anatomia da conexão

```java
// ChatWebSocketConfig.java
registry.addHandler(chatWebSocketHandler, "/ws/chat")
    .setAllowedOriginPatterns(allowedOriginPatterns);
```

O cliente conecta com `ws://host:3000/ws/chat?conversationId=<id>`. Três
métodos do `TextWebSocketHandler` cobrem todo o ciclo de vida:

```java
afterConnectionEstablished(session)  // conexão aberta: registra a sessão, manda o replay
handleTextMessage(session, message)  // uma mensagem JSON chegou do cliente
afterConnectionClosed(session, status) // conexão fechada: apenas desregistra
```

Note que `afterConnectionClosed` **não** cancela nada em andamento — só
remove a sessão do registro. Isso é proposital (seção 6).

## 4. O protocolo: um envelope, um campo `type`

Em vez de um tipo de mensagem por classe, tanto cliente→servidor quanto
servidor→cliente usam um **envelope único** com um campo discriminador
`type` — o mesmo padrão usado por APIs de streaming de chat no mercado
(ex.: eventos de streaming da OpenAI/Anthropic).

```json
// cliente → servidor
{"type": "user_message", "content": "Qual a validade de uma banana media?"}
{"type": "interrupt"}

// servidor → cliente
{"type": "reasoning_chunk", "content": "A banana"}
{"type": "status_changed", "status": "PROCESSING"}
```

Vantagem prática: o cliente faz `JSON.parse` + `switch (event.type)` — sem
hierarquia de classes, sem `instanceof`. Do lado do backend, dois `record`s
cobrem tudo (`IncomingChatMessage`, `OutgoingChatEvent`), cada evento
representado por uma fábrica estática que só popula os campos relevantes.

## 5. Streaming: a resposta chegando em pedaços

O modelo (via Spring AI) devolve um `Flux<String>` — um pedaço de texto por
elemento. `ChatReasoningRunner` assina esse `Flux` e publica cada pedaço
como um evento `reasoning_chunk`:

```java
this.reasoningModel.streamReply(chat.history())
    .doOnNext(chunk -> this.events.publishReasoningChunk(chat.id(), chunk))
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe(...)
```

`subscribeOn(Schedulers.boundedElastic())` é o que faz `runAsync()` retornar
na hora — a assinatura do `Flux` roda em outra thread, então o handler do
WebSocket nunca fica bloqueado esperando o modelo responder.

No cliente, cada `reasoning_chunk` só concatena texto num buffer local
(`useChat.ts`); a mensagem "oficial" (persistida) só chega no fim, via
`message_appended`.

## 6. Estado no servidor, não na conexão

Um detalhe fácil de errar: o streaming **não depende** da sessão WebSocket
estar aberta. O `Flux` do modelo continua rodando mesmo que o usuário feche
a aba — porque a subscrição vive no servidor (`ChatReasoningRunner`), não
"dentro" da conexão.

Isso é o que viabiliza a reconexão sem polling: ao reconectar (mesmo
`conversationId`), `afterConnectionEstablished` manda um evento `replay`
com o histórico completo + status atual — incluindo qualquer coisa que
tenha sido gerada enquanto o cliente estava offline.

## 7. Parar e substituir: o "stop" por baixo dos panos

`ChatReasoningRunner` guarda a rodada ativa por conversa num
`Map<ChatId, ActiveReasoning>`. Interromper significa: `dispose()` no
`Disposable` da subscrição (o `Flux` para de emitir) e preservar o texto já
acumulado como uma mensagem marcada `interrupted: true`.

Mandar uma nova mensagem **enquanto o modelo ainda responde** (sem apertar
"parar" antes) segue o mesmo caminho — `SendUserMessageService` chama
`interrupt()` antes de processar a nova mensagem, então o efeito é idêntico
ao clicar "parar" e mandar de novo, só que em um passo:

```java
if (this.reasoningRunner.interrupt(chatId)) {
    log.info("[{}] geração anterior interrompida", chatId);
}
// ... anexa a nova mensagem e inicia uma nova rodada
```

## 8. Reconexão no cliente

`useChatSocket.ts` reabre a conexão automaticamente com backoff exponencial
(500ms, 1s, 2s... até 8s) sempre que o socket fecha de forma inesperada —
sem intervenção do usuário. Como o servidor manda o `replay` assim que a
conexão reabre, a UI volta ao estado correto sozinha.

## 9. Testando WebSocket sem um servidor real

Dois níveis de teste cobrem o protocolo:

- **Unitário**: os *use cases* recebem `ChatEventPublisher`/`ReasoningModelPort`
  mockados — nenhuma dependência de WebSocket ou Ollama real.
- **Integração** (`ChatWebSocketHandlerIT`): sobe o Spring context completo e
  conecta de verdade via `StandardWebSocketClient`, mas troca o modelo real
  por um `ReasoningModelPort` determinístico (`@TestConfiguration`), então
  os testes são rápidos e não dependem de infraestrutura externa.

```bash
mvn verify   # unitários (Surefire) + integração via WebSocket real (Failsafe)
```
