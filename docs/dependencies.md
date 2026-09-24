# Dependências — o quê e por quê

Lista completa das bibliotecas de produção usadas no backend e no frontend,
e a razão de cada escolha. Dependências de build (plugins Maven, `tsc`) não
estão listadas — só bibliotecas que viram código em runtime.

## Backend (`pom.xml`)

| Dependência | Papel | Por que essa e não outra |
|---|---|---|
| `spring-boot-starter-parent` (3.3.2) | BOM + configuração padrão do Spring Boot. | Fixa versões compatíveis entre si (evita conflito de versões transitivas) e traz o plugin de empacotamento (`spring-boot-maven-plugin`). |
| `spring-boot-starter-websocket` | `TextWebSocketHandler`, infraestrutura de handshake HTTP → WS. | É todo o suporte a WebSocket que este projeto precisa — nenhum STOMP/broker, então nenhum starter maior. Ver [`websocket-explained.md`](websocket-explained.md#2-websocket-cru-vs-stomp). |
| `jackson-databind` | Serialização JSON (`ObjectMapper`) do protocolo WS. | Já vem transitivamente com boa parte do Spring, mas é declarada explicitamente porque é usada diretamente (`ChatWebSocketHandler`, `ChatWebSocketEventBroadcaster`) — deixa a dependência real visível no `pom.xml`, não escondida atrás de outra lib. |
| `spring-ai-starter-model-ollama` (1.0.0) | `ChatClient` + autoconfiguração do `OllamaChatModel` a partir de `application.yaml`. | É o que permite trocar o provedor do LLM (Ollama → OpenAI/Anthropic/etc.) mudando só o adapter (`OllamaReasoningModelAdapter`), sem tocar no `core`. Ollama foi escolhido para a POC por rodar 100% local, sem custo de API nem chave. |
| `spring-boot-starter-test` (*test*) | JUnit 5, Mockito, AssertJ, utilitários de teste do Spring. | Padrão de fato para testes em projetos Spring Boot — cobre unit e integration test sem juntar bibliotecas soltas. |
| `awaitility` (*test*) | Assertivas assíncronas (`await().untilAsserted(...)`). | O raciocínio roda em outra thread (`Schedulers.boundedElastic()`); sem Awaitility os testes teriam que fazer polling manual ou `Thread.sleep`, ambos piores (flaky ou lentos). |

> `spring-boot-starter-validation` foi removida: nenhum DTO usa Bean
> Validation (`@NotBlank`, `@Valid` etc.) neste projeto — mantê-la seria uma
> dependência morta.

## Frontend (`frontend/package.json`)

| Dependência | Papel | Por que essa e não outra |
|---|---|---|
| `react` / `react-dom` (18.3.1) | Biblioteca de UI declarativa. | Componentização natural para uma tela de chat (lista de mensagens, composer, header, cada um seu componente); ecossistema/convenção já usada em outros frontends do time. |
| `typescript` (5.6) | Tipagem estática. | `types/chat.ts` espelha 1:1 os DTOs do backend (`IncomingChatMessage`/`OutgoingChatEvent`) — um campo renomeado ou removido no protocolo quebra o build do frontend em vez de falhar silenciosamente em runtime. |
| `vite` (5.4) | Dev server + bundler. | Build de produção simples (`npm run build` gera direto em `target/classes/static`, de onde o Spring Boot já serve) e hot reload rápido em desenvolvimento (`npm run dev`, com proxy do WebSocket para o backend real). |
| `@vitejs/plugin-react` (4.3) | Suporte a JSX/Fast Refresh dentro do Vite. | Exigido pelo Vite para projetos React — sem ele, `.tsx` não compila. |

Nenhuma outra dependência de runtime: sem biblioteca de state management
(o estado da conversa cabe em alguns `useState`/`useRef` — ver
[`architecture.md`](architecture.md#front-end)), sem kit de componentes UI
(estilo próprio em `index.css`, tema azul/branco). Minimalismo deliberado:
menos superfície para manter numa POC.
