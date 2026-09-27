# Instruções para o agente de codificação

Este arquivo é lido pelo agente de codificação no início de cada tarefa
(ver [docs/coding-agent.md](docs/coding-agent.md)). Ele está escrito para alguém que
acabou de chegar no projeto: curto, concreto e com os comandos exatos.

## O projeto

Chat com streaming de LLM sobre WebSocket. Backend em Java 21 + Spring Boot 3 + Spring AI,
seguindo Ports & Adapters. Frontend em React + TypeScript em `frontend/`.

## Comandos (o ambiente NÃO tem internet: use sempre `-o`)

- Compilar: `mvn -B -o -q compile`
- Testes unitários: `mvn -B -o -q test`
- Um teste só: `mvn -B -o -q test -Dtest=NomeDoTeste`

O frontend não é verificado automaticamente neste ambiente (não há Node instalado).
Se alterar arquivos em `frontend/src`, diga isso no resumo final.

## Arquitetura: onde cada coisa vai

- `core/<contexto>/domain`: entidades e portas. Nunca importa Spring nem nada de `adapters`.
- `core/<contexto>/usecase`: uma interface por caso de uso.
- `core/<contexto>/application`: implementações dos casos de uso (`@Service`).
- `adapters/<contexto>/...`: WebSocket, Ollama, GitHub, persistência em memória.
- Endpoints HTTP novos (ex.: health check) ficam em `adapters/<contexto>/web`.

## Convenções

- Código e comentários em português do Brasil, como o resto do projeto.
- Todo comportamento novo precisa de teste unitário em `src/test/java`, no mesmo pacote.
  Os testes usam JUnit 5, AssertJ e Mockito (veja `SendUserMessageServiceTest` como modelo).
- Não adicione dependências no `pom.xml`: o ambiente não tem internet. Se for indispensável,
  explique no resumo e termine com status `incomplete`.

## Não mexa

- `.github/workflows/`
- `.agent/`
- `src/main/resources/application.yaml`, exceto se a tarefa pedir explicitamente.
