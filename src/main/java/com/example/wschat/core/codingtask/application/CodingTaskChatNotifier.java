package com.example.wschat.core.codingtask.application;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.ChatRole;
import com.example.wschat.core.chat.domain.event.ChatEventPublisher;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.event.CodingTaskEventPublisher;

/**
 * Faz a ponte entre as tarefas de codificação e a conversa.
 *
 * Cada marco importante vira uma mensagem do assistente no histórico do chat. Assim o
 * usuário vê tudo na conversa, a reconexão (replay) já traz o que aconteceu enquanto ele
 * estava fora, e o próprio LLM do chat passa a "saber" do PR nas próximas perguntas.
 */
@Component
public class CodingTaskChatNotifier {

    private final ChatRepository chatRepository;
    private final ChatEventPublisher chatEvents;
    private final CodingTaskEventPublisher taskEvents;

    public CodingTaskChatNotifier(ChatRepository chatRepository, ChatEventPublisher chatEvents, CodingTaskEventPublisher taskEvents) {
        this.chatRepository = chatRepository;
        this.chatEvents = chatEvents;
        this.taskEvents = taskEvents;
    }

    /** Estado da tarefa mudou: atualiza o cartão da tarefa na interface. */
    public void taskChanged(CodingTask task) {
        this.taskEvents.publishTaskUpdated(task);
    }

    public void userRequested(ChatId chatId, String request) {
        this.appendToChat(chatId, ChatRole.USER, "/codificar " + request);
    }

    public void started(CodingTask task) {
        this.appendToChat(task.chatId(), ChatRole.ASSISTANT, """
            Abri a issue #%d em %s e disparei o agente de codificação.
            Issue: %s
            Branch de trabalho: %s
            Aviso aqui quando o PR em rascunho estiver pronto.""".formatted(
            task.issueNumber(), task.repository(), task.issueUrl(), task.workBranch()));
    }

    public void couldNotStart(ChatId chatId, String reason) {
        this.appendToChat(chatId, ChatRole.ASSISTANT, "Não consegui iniciar a codificação: " + reason);
    }

    public void finished(CodingTask task) {
        var result = task.result();
        var text = new StringBuilder();
        if (result.prUrl() != null) {
            text.append("O agente terminou a issue #").append(task.issueNumber()).append(".\n")
                .append("PR em rascunho, aguardando sua revisão: ").append(result.prUrl()).append('\n');
        } else {
            text.append("O agente terminou a issue #").append(task.issueNumber()).append(" sem abrir PR.\n");
        }
        if (task.errorMessage() != null) {
            text.append("Problema: ").append(task.errorMessage()).append('\n');
        }
        if (result.agentStatus() != null) {
            text.append("Status do agente: ").append(result.agentStatus()).append('\n');
        }
        if (result.verification() != null) {
            text.append("Verificação: ").append(result.verification()).append('\n');
        }
        if (!result.changedFiles().isEmpty()) {
            text.append("Arquivos alterados:\n")
                .append(result.changedFiles().stream().map(file -> "- " + file).collect(Collectors.joining("\n")))
                .append('\n');
        }
        if (result.summary() != null && !result.summary().isBlank()) {
            text.append("\nResumo do agente:\n").append(result.summary().strip()).append('\n');
        }
        if (task.runUrl() != null) {
            text.append("\nLog da execução: ").append(task.runUrl());
        }
        this.appendToChat(task.chatId(), ChatRole.ASSISTANT, text.toString().strip());
    }

    private void appendToChat(ChatId chatId, ChatRole role, String content) {
        var chat = this.chatRepository.findOrCreate(chatId);
        var message = chat.appendMessage(role, content);
        this.chatRepository.save(chat);
        this.chatEvents.publishMessageAppended(chatId, message);
    }
}
