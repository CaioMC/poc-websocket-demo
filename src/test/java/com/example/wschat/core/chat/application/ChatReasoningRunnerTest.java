package com.example.wschat.core.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.ai.ReasoningModelPort;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

@ExtendWith(MockitoExtension.class)
class ChatReasoningRunnerTest {

    private static final ChatId CHAT_ID = ChatId.of("chat-1");

    @Mock
    private ChatRepository repository;

    @Mock
    private ReasoningModelPort reasoningModel;

    @Mock
    private ChatEventPublisher events;

    private ChatReasoningRunner runner;
    private Chat chat;

    @BeforeEach
    void setUp() {
        chat = Chat.startNew(CHAT_ID);
        // lenient: só os cenários que efetivamente interrompem uma rodada usam esse lookup.
        lenient().when(repository.findOrCreate(CHAT_ID)).thenReturn(chat);
        runner = new ChatReasoningRunner(repository, reasoningModel, events);
    }

    @Test
    void deveTransmitirChunksEFinalizarComoIdle() {
        when(reasoningModel.streamReply(any())).thenReturn(Flux.just("Olá", ", tudo bem?"));

        runner.runAsync(chat);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(chat.history()).hasSize(1);
            assertThat(chat.history().get(0).content()).isEqualTo("Olá, tudo bem?");
            assertThat(chat.status()).isEqualTo(ChatStatus.IDLE);
        });

        verify(events, times(2)).publishReasoningChunk(eq(CHAT_ID), any());
        verify(events).publishStatusChanged(CHAT_ID, ChatStatus.PROCESSING);
        verify(events).publishStatusChanged(CHAT_ID, ChatStatus.IDLE);
        verify(events).publishMessageAppended(eq(CHAT_ID), any());
        verify(events).publishReasoningCompleted(CHAT_ID);
    }

    @Test
    void deveDefinirWaitingForContextQuandoModeloPedirMaisInformacao() {
        when(reasoningModel.streamReply(any()))
            .thenReturn(Flux.just(ChatReasoningRunner.NEEDS_CONTEXT_PREFIX + ": qual o número do pedido?"));

        runner.runAsync(chat);

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(chat.status()).isEqualTo(ChatStatus.WAITING_FOR_CONTEXT));
    }

    @Test
    void deveMarcarStatusErrorQuandoModeloFalhar() {
        when(reasoningModel.streamReply(any())).thenReturn(Flux.error(new RuntimeException("modelo indisponível")));

        runner.runAsync(chat);

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(chat.status()).isEqualTo(ChatStatus.ERROR));
        verify(events).publishError(eq(CHAT_ID), any());
    }

    @Test
    void deveMarcarStatusErrorQuandoRespostaForVazia() {
        when(reasoningModel.streamReply(any())).thenReturn(Flux.empty());

        runner.runAsync(chat);

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(chat.status()).isEqualTo(ChatStatus.ERROR));
        assertThat(chat.history()).isEmpty();
    }

    @Test
    void interruptDeveRetornarFalseQuandoNaoHaRodadaEmAndamento() {
        assertThat(runner.interrupt(CHAT_ID)).isFalse();
        verify(events, never()).publishReasoningInterrupted(any());
    }

    @Test
    void interruptDevePreservarConteudoParcialEVoltarParaIdle() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(reasoningModel.streamReply(any())).thenReturn(sink.asFlux());

        runner.runAsync(chat);
        sink.tryEmitNext("Bananas duram, em média,");

        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> verify(events).publishReasoningChunk(CHAT_ID, "Bananas duram, em média,"));

        var interrupted = runner.interrupt(CHAT_ID);

        assertThat(interrupted).isTrue();
        assertThat(chat.status()).isEqualTo(ChatStatus.IDLE);
        assertThat(chat.history()).hasSize(1);
        assertThat(chat.history().get(0).role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(chat.history().get(0).content()).isEqualTo("Bananas duram, em média,");
        assertThat(chat.history().get(0).interrupted()).isTrue();
        verify(events).publishReasoningInterrupted(CHAT_ID);

        // Uma segunda chamada não deve fazer nada: a rodada já foi encerrada.
        assertThat(runner.interrupt(CHAT_ID)).isFalse();
    }

    @Test
    void runAsyncDeveInterromperRodadaAnteriorAntesDeIniciarUmaNova() {
        Sinks.Many<String> firstSink = Sinks.many().unicast().onBackpressureBuffer();
        when(reasoningModel.streamReply(any()))
            .thenReturn(firstSink.asFlux())
            .thenReturn(Flux.just("Nova resposta completa"));

        runner.runAsync(chat);
        firstSink.tryEmitNext("resposta antiga parcial");
        await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> verify(events).publishReasoningChunk(CHAT_ID, "resposta antiga parcial"));

        // Mesma sequência que SendUserMessageService segue ao receber uma nova
        // mensagem durante o processamento: interrompe explicitamente primeiro
        // (preservando o parcial no histórico), só então anexa a nova pergunta
        // e dispara uma nova rodada.
        runner.interrupt(CHAT_ID);
        chat.appendMessage(ChatRole.USER, "Muda de assunto");
        runner.runAsync(chat);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(chat.history()).hasSize(3);
            assertThat(chat.history().get(0).content()).isEqualTo("resposta antiga parcial");
            assertThat(chat.history().get(0).interrupted()).isTrue();
            assertThat(chat.history().get(1).content()).isEqualTo("Muda de assunto");
            assertThat(chat.history().get(2).content()).isEqualTo("Nova resposta completa");
            assertThat(chat.status()).isEqualTo(ChatStatus.IDLE);
        });
        verify(events).publishReasoningInterrupted(CHAT_ID);
    }
}
