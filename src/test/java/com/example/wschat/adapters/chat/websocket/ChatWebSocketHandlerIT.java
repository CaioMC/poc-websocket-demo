package com.example.wschat.adapters.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.wschat.core.chat.domain.ai.ReasoningModelPort;

/**
 * Teste de integração ponta a ponta do fluxo de WebSocket, cobrindo os 3
 * cenários da POC: streaming de resposta, contexto adicional e reconexão
 * (replay). Usa um {@link ReasoningModelPort} falso (determinístico) no
 * lugar do Ollama real, para o teste ser rápido e não depender de
 * infraestrutura externa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketHandlerIT {

    @TestConfiguration
    static class FakeReasoningModelConfig {

        @Bean
        @Primary
        ReasoningModelPort fakeReasoningModelPort() {
            return history -> {
                var lastUserContent = history.get(history.size() - 1).content();
                if (lastUserContent.contains("devagar")) {
                    // Emite um pedaço e some por um tempo, dando espaço para o teste interromper no meio.
                    return Flux.concat(Flux.just("Deixa eu pensar"), Flux.never());
                }
                if (lastUserContent.contains("pedido") && !lastUserContent.contains("123")) {
                    return Flux.just("Preciso de mais informações", ": qual o número do pedido?");
                }
                return Flux.just("Tudo certo, ", "resposta finalizada.");
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketSession session;
    private BlockingQueue<JsonNode> receivedEvents;

    @BeforeEach
    void setUp() throws Exception {
        receivedEvents = new LinkedBlockingQueue<>();
        var client = new StandardWebSocketClient();
        var chatId = "it-" + System.nanoTime();
        var uri = URI.create("ws://localhost:%d/ws/chat?conversationId=%s".formatted(port, chatId));

        WebSocketHandler captureHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                receivedEvents.add(objectMapper.readTree(message.getPayload()));
            }
        };

        session = client.execute(captureHandler, uri.toString()).get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null && session.isOpen()) {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void deveReceberReplayImediatoAoConectar() throws Exception {
        var replay = takeEvent();

        assertThat(replay.get("type").asText()).isEqualTo("replay");
        assertThat(replay.get("status").asText()).isEqualTo("IDLE");
        assertThat(replay.get("messages")).isEmpty();
    }

    @Test
    void deveTransmitirRespostaEmStreamingAposMensagemDoUsuario() throws Exception {
        takeEvent(); // replay inicial

        session.sendMessage(new TextMessage("""
            {"type":"user_message","content":"Olá, tudo certo por aqui"}
            """));

        var userAppended = takeEvent();
        assertThat(userAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(userAppended.get("message").get("role").asText()).isEqualTo("USER");

        var processing = takeEvent();
        assertThat(processing.get("type").asText()).isEqualTo("status_changed");
        assertThat(processing.get("status").asText()).isEqualTo("PROCESSING");

        var chunk1 = takeEvent();
        assertThat(chunk1.get("type").asText()).isEqualTo("reasoning_chunk");
        var chunk2 = takeEvent();
        assertThat(chunk2.get("type").asText()).isEqualTo("reasoning_chunk");
        assertThat(chunk1.get("content").asText() + chunk2.get("content").asText())
            .isEqualTo("Tudo certo, resposta finalizada.");

        var assistantAppended = takeEvent();
        assertThat(assistantAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(assistantAppended.get("message").get("role").asText()).isEqualTo("ASSISTANT");

        var finalStatus = takeEvent();
        assertThat(finalStatus.get("type").asText()).isEqualTo("status_changed");
        assertThat(finalStatus.get("status").asText()).isEqualTo("IDLE");

        // "completed" é o último evento da rodada, já com o status final conhecido.
        var completed = takeEvent();
        assertThat(completed.get("type").asText()).isEqualTo("reasoning_completed");
    }

    @Test
    void deveEsperarContextoERetomarAoReceberComplemento() throws Exception {
        takeEvent(); // replay inicial

        session.sendMessage(new TextMessage("""
            {"type":"user_message","content":"Quero saber do meu pedido"}
            """));

        // Consome a rodada inteira (que termina em "reasoning_completed") e guarda
        // o último status observado, que deve ser WAITING_FOR_CONTEXT.
        assertThat(consumeRoundAndReturnFinalStatus()).isEqualTo("WAITING_FOR_CONTEXT");

        session.sendMessage(new TextMessage("""
            {"type":"context","content":"Numero do pedido: 123"}
            """));

        var contextAppended = takeEvent();
        assertThat(contextAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(contextAppended.get("message").get("role").asText()).isEqualTo("CONTEXT");

        assertThat(consumeRoundAndReturnFinalStatus()).isEqualTo("IDLE");
    }

    @Test
    void deveInterromperGeracaoEPreservarParcialAoReceberComandoDeInterrupcao() throws Exception {
        takeEvent(); // replay inicial

        session.sendMessage(new TextMessage("""
            {"type":"user_message","content":"Responde bem devagar"}
            """));

        takeEvent(); // message_appended (usuário)
        takeEvent(); // status_changed -> PROCESSING
        var chunk = takeEvent();
        assertThat(chunk.get("type").asText()).isEqualTo("reasoning_chunk");
        assertThat(chunk.get("content").asText()).isEqualTo("Deixa eu pensar");

        session.sendMessage(new TextMessage("""
            {"type":"interrupt"}
            """));

        var partialAppended = takeEvent();
        assertThat(partialAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(partialAppended.get("message").get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(partialAppended.get("message").get("content").asText()).isEqualTo("Deixa eu pensar");
        assertThat(partialAppended.get("message").get("interrupted").asBoolean()).isTrue();

        var status = takeEvent();
        assertThat(status.get("type").asText()).isEqualTo("status_changed");
        assertThat(status.get("status").asText()).isEqualTo("IDLE");

        var interrupted = takeEvent();
        assertThat(interrupted.get("type").asText()).isEqualTo("reasoning_interrupted");
    }

    @Test
    void deveSubstituirRespostaEmAndamentoAoReceberNovaMensagemDoUsuario() throws Exception {
        takeEvent(); // replay inicial

        session.sendMessage(new TextMessage("""
            {"type":"user_message","content":"Responde bem devagar"}
            """));
        takeEvent(); // message_appended (usuário)
        takeEvent(); // status_changed -> PROCESSING
        var chunk = takeEvent();
        assertThat(chunk.get("content").asText()).isEqualTo("Deixa eu pensar");

        // Manda uma segunda mensagem antes da primeira resposta terminar — deve
        // interromper a rodada anterior (preservando o parcial) e iniciar outra.
        session.sendMessage(new TextMessage("""
            {"type":"user_message","content":"Esquece, me diz outra coisa"}
            """));

        var partialAppended = takeEvent();
        assertThat(partialAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(partialAppended.get("message").get("interrupted").asBoolean()).isTrue();

        var idleAfterInterrupt = takeEvent();
        assertThat(idleAfterInterrupt.get("type").asText()).isEqualTo("status_changed");
        assertThat(idleAfterInterrupt.get("status").asText()).isEqualTo("IDLE");

        var interruptedEvent = takeEvent();
        assertThat(interruptedEvent.get("type").asText()).isEqualTo("reasoning_interrupted");

        var newUserAppended = takeEvent();
        assertThat(newUserAppended.get("type").asText()).isEqualTo("message_appended");
        assertThat(newUserAppended.get("message").get("role").asText()).isEqualTo("USER");
        assertThat(newUserAppended.get("message").get("content").asText()).isEqualTo("Esquece, me diz outra coisa");

        assertThat(consumeRoundAndReturnFinalStatus()).isEqualTo("IDLE");
    }

    /**
     * Consome eventos até o fim da rodada de raciocínio ({@code reasoning_completed},
     * sempre o último evento publicado) e devolve o último {@code status_changed}
     * observado no caminho.
     */
    private String consumeRoundAndReturnFinalStatus() throws InterruptedException {
        String lastStatus = null;
        JsonNode event;
        do {
            event = takeEvent();
            if (event.get("type").asText().equals("status_changed")) {
                lastStatus = event.get("status").asText();
            }
        } while (!event.get("type").asText().equals("reasoning_completed"));
        return lastStatus;
    }

    private JsonNode takeEvent() throws InterruptedException {
        var event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).as("esperava receber um evento em até 5s").isNotNull();
        return event;
    }
}
