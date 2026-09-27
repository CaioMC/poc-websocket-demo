package com.example.wschat.core.codingtask.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.chat.domain.repository.ChatRepository;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort;
import com.example.wschat.core.codingtask.domain.repository.CodingTaskRepository;
import com.example.wschat.core.codingtask.usecase.StartCodingTaskUseCase;

/**
 * O "gatilho" do agente de codificação.
 *
 * <ol>
 *   <li>registra o pedido do usuário na conversa;</li>
 *   <li>abre uma issue com o pedido e o contexto recente da conversa;</li>
 *   <li>dispara o workflow do agente para essa issue;</li>
 *   <li>avisa no chat, com os links.</li>
 * </ol>
 * Daqui em diante, quem acompanha é o {@link TrackCodingTasksService}.
 */
@Service
public class StartCodingTaskService implements StartCodingTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartCodingTaskService.class);

    private final CodingAgentPort codingAgent;
    private final CodingTaskRepository tasks;
    private final ChatRepository chats;
    private final CodingTaskChatNotifier notifier;
    private final CodingTaskSettings settings;

    public StartCodingTaskService(CodingAgentPort codingAgent, CodingTaskRepository tasks, ChatRepository chats,
                                  CodingTaskChatNotifier notifier, CodingTaskSettings settings) {
        this.codingAgent = codingAgent;
        this.tasks = tasks;
        this.chats = chats;
        this.notifier = notifier;
        this.settings = settings;
    }

    @Override
    public CodingTask start(ChatId chatId, String request) {
        // Contexto capturado ANTES de registrar o pedido, para a issue não repetir o próprio comando.
        var history = this.chats.findOrCreate(chatId).history();
        // List.copyOf: o histórico é um CopyOnWriteArrayList e uma sublista "viva" quebraria
        // assim que a mensagem do pedido for anexada logo abaixo.
        var recentConversation = List.copyOf(
            history.subList(Math.max(0, history.size() - this.settings.contextMessages()), history.size()));

        this.notifier.userRequested(chatId, request == null ? "" : request.strip());

        CodingTask task = null;
        try {
            var parsed = CodingTaskRequestParser.parse(request, this.settings.defaultRepository());
            task = CodingTask.request(chatId, parsed.repository(), this.settings.baseBranch(), parsed.title(), parsed.description());
            this.tasks.save(task);

            var issue = this.codingAgent.openIssue(task.repository(), task.title(),
                IssueBodyBuilder.build(parsed.description(), recentConversation));
            task.issueOpened(issue.number(), issue.url());
            this.save(task);

            this.codingAgent.dispatch(task);
            task.dispatched();
            this.save(task);

            log.info("[{}] tarefa {} disparada: issue #{} em {}", chatId, task.id(), task.issueNumber(), task.repository());
            this.notifier.started(task);
            return task;
        } catch (RuntimeException e) {
            log.warn("[{}] falha ao iniciar tarefa de codificação: {}", chatId, e.getMessage());
            if (task != null) {
                task.failed(e.getMessage(), null);
                this.save(task);
            }
            this.notifier.couldNotStart(chatId, e.getMessage());
            return task;
        }
    }

    private void save(CodingTask task) {
        this.tasks.save(task);
        this.notifier.taskChanged(task);
    }
}
