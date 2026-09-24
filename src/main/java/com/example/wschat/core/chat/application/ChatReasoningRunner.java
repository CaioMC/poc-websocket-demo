package com.example.wschat.core.chat.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import com.example.wschat.core.chat.domain.Chat;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.ChatStatus;
import com.example.wschat.core.chat.domain.ai.ReasoningModelPort;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;

/**
 * Orquestra as rodadas de raciocínio do modelo. Bean único, compartilhado por
 * todos os use cases que disparam raciocínio, pois mantém o registro das
 * execuções em andamento — é o que permite {@link #interrupt(ChatId)}.
 */
@Component
public class ChatReasoningRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatReasoningRunner.class);

    /** Prefixo que o system prompt instrui o modelo a usar ao precisar de mais contexto. */
    public static final String NEEDS_CONTEXT_PREFIX = "Preciso de mais informações";

    private final ChatRepository repository;
    private final ReasoningModelPort reasoningModel;
    private final ChatEventPublisher events;

    /** Rodada em andamento por conversa (no máximo uma por chat). */
    private final Map<ChatId, ActiveReasoning> activeReasonings = new ConcurrentHashMap<>();

    public ChatReasoningRunner(ChatRepository repository, ReasoningModelPort reasoningModel, ChatEventPublisher events) {
        this.repository = repository;
        this.reasoningModel = reasoningModel;
        this.events = events;
    }

    public void runAsync(Chat chat) {
        this.interrupt(chat.id());

        var execution = new ActiveReasoning();
        this.activeReasonings.put(chat.id(), execution);

        chat.changeStatus(ChatStatus.PROCESSING);
        this.repository.save(chat);
        this.events.publishStatusChanged(chat.id(), ChatStatus.PROCESSING);

        execution.subscription = this.reasoningModel.streamReply(chat.history())
            .doOnNext(chunk -> {
                execution.buffer.append(chunk);
                this.events.publishReasoningChunk(chat.id(), chunk);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                ignored -> { /* chunks já tratados em doOnNext */ },
                error -> this.finish(chat, execution, () -> this.onError(chat, error)),
                () -> this.finish(chat, execution, () -> this.onComplete(chat, execution.buffer.toString()))
            );
    }

    /**
     * Interrompe a geração em andamento, preservando o trecho parcial no histórico.
     *
     * @return {@code true} se havia uma geração em andamento que foi interrompida.
     */
    public boolean interrupt(ChatId chatId) {
        var execution = this.takeActiveReasoning(chatId);
        if (execution == null) {
            return false;
        }

        var chat = this.repository.findOrCreate(chatId);
        var partialContent = execution.buffer.toString();
        if (!partialContent.isBlank()) {
            var message = chat.appendInterruptedAssistantMessage(partialContent);
            this.events.publishMessageAppended(chatId, message);
        }

        chat.changeStatus(ChatStatus.IDLE);
        this.repository.save(chat);
        this.events.publishStatusChanged(chatId, ChatStatus.IDLE);
        this.events.publishReasoningInterrupted(chatId);

        log.info("[{}] geração interrompida pelo usuário ({} caracteres preservados)", chatId, partialContent.length());
        return true;
    }

    /** Cancela a rodada em andamento sem preservar o parcial nem publicar eventos (usado no reset). */
    public void discardActiveReasoning(ChatId chatId) {
        this.takeActiveReasoning(chatId);
    }

    /** Remove e encerra a rodada corrente, via CAS, garantindo que só um chamador a finalize. */
    private ActiveReasoning takeActiveReasoning(ChatId chatId) {
        var execution = this.activeReasonings.remove(chatId);
        if (execution == null || !execution.finished.compareAndSet(false, true)) {
            return null;
        }
        if (execution.subscription != null) {
            execution.subscription.dispose();
        }
        return execution;
    }

    /** Só finaliza se a rodada ainda for a corrente — evita sobrescrever um estado já interrompido/substituído. */
    private void finish(Chat chat, ActiveReasoning execution, Runnable completion) {
        if (!execution.finished.compareAndSet(false, true)) {
            return;
        }
        this.activeReasonings.remove(chat.id(), execution);
        completion.run();
    }

    private void onComplete(Chat chat, String fullContent) {
        if (fullContent.isBlank()) {
            this.onError(chat, new IllegalStateException("Modelo retornou uma resposta vazia"));
            return;
        }

        chat.appendMessage(ChatRole.ASSISTANT, fullContent);
        var nextStatus = fullContent.stripLeading().startsWith(NEEDS_CONTEXT_PREFIX)
            ? ChatStatus.WAITING_FOR_CONTEXT
            : ChatStatus.IDLE;
        chat.changeStatus(nextStatus);
        this.repository.save(chat);

        this.events.publishMessageAppended(chat.id(), chat.history().get(chat.history().size() - 1));
        this.events.publishStatusChanged(chat.id(), nextStatus);
        // Último evento da rodada: o cliente pode parar de escutar ao recebê-lo.
        this.events.publishReasoningCompleted(chat.id());
    }

    private void onError(Chat chat, Throwable error) {
        log.warn("[{}] falha ao processar raciocínio: {}", chat.id(), error.getMessage(), error);
        chat.changeStatus(ChatStatus.ERROR);
        this.repository.save(chat);
        this.events.publishError(chat.id(), "Falha ao consultar o modelo: " + error.getMessage());
        this.events.publishStatusChanged(chat.id(), ChatStatus.ERROR);
    }

    private static final class ActiveReasoning {

        /** StringBuffer (não StringBuilder): escrito pela thread do streaming, lido pela thread que interrompe. */
        private final StringBuffer buffer = new StringBuffer();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile Disposable subscription;
    }
}
