package com.example.wschat.adapters.codingtask.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.example.wschat.adapters.codingtask.config.CodingAgentProperties;
import com.example.wschat.core.chat.domain.ChatId;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.RunProgress.Phase;

/**
 * Testa o adapter contra um servidor HTTP local que imita a API do GitHub:
 * issue, dispatch, busca da execução pelo nome, status e download do artefato.
 */
class GitHubCodingAgentAdapterTest {

    private HttpServer server;
    private String baseUrl;
    private GitHubCodingAgentAdapter adapter;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> dispatchBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/CaioMC/app/issues", exchange ->
            reply(exchange, 201, "{\"number\":12,\"html_url\":\"https://github.com/CaioMC/app/issues/12\"}"));
        server.createContext("/repos/CaioMC/app/actions/workflows/coding-agent.yml/dispatches", exchange -> {
            dispatchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 204, "");
        });
        server.createContext("/repos/CaioMC/app/actions/workflows/coding-agent.yml/runs", exchange -> {
            var requestId = mapper.readTree(dispatchBody.get()).path("inputs").path("request_id").asText();
            reply(exchange, 200, "{\"workflow_runs\":[{\"id\":1,\"display_title\":\"coding-agent outro\"},"
                + "{\"id\":77,\"display_title\":\"coding-agent " + requestId + "\"}]}");
        });
        server.createContext("/repos/CaioMC/app/actions/runs/77", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/artifacts")) {
                reply(exchange, 200, "{\"artifacts\":[{\"name\":\"agent-result\",\"expired\":false,"
                    + "\"archive_download_url\":\"" + baseUrl + "/download\"}]}");
            } else {
                reply(exchange, 200, "{\"status\":\"completed\",\"conclusion\":\"success\",\"html_url\":\"https://run/77\"}");
            }
        });
        server.createContext("/download", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/blob");
            reply(exchange, 302, "");
        });
        server.createContext("/blob", exchange -> {
            // O storage de artefatos não deve receber o token.
            if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
                reply(exchange, 400, "");
                return;
            }
            var zip = zip("agent-result/result.json", """
                {"status":"COMPLETED","summary":"Feito","changedFiles":["src/A.java"],
                 "verification":{"command":"bash .agent/verify.sh","executed":true,"exitCode":0},
                 "publish":{"prUrl":"https://github.com/CaioMC/app/pull/13"}}""");
            exchange.sendResponseHeaders(200, zip.length);
            exchange.getResponseBody().write(zip);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var properties = new CodingAgentProperties("CaioMC/app", "main", "coding-agent.yml", 15000,
            Duration.ofMinutes(10), 6, new CodingAgentProperties.Github(baseUrl, "token-de-teste"));
        adapter = new GitHubCodingAgentAdapter(properties, mapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void deveAbrirIssueDispararEAcompanharAteOResultado() throws Exception {
        var task = CodingTask.request(ChatId.of("c"), "CaioMC/app", "main", "Criar endpoint", "Criar endpoint");

        var issue = adapter.openIssue("CaioMC/app", task.title(), "corpo");
        task.issueOpened(issue.number(), issue.url());
        adapter.dispatch(task);

        var inputs = mapper.readTree(dispatchBody.get()).path("inputs");
        assertThat(mapper.readTree(dispatchBody.get()).path("ref").asText()).isEqualTo("main");
        assertThat(inputs.path("issue_number").asText()).isEqualTo("12");
        assertThat(inputs.path("work_branch").asText()).isEqualTo(task.workBranch());

        var progress = adapter.checkProgress(task);

        assertThat(progress.phase()).isEqualTo(Phase.SUCCEEDED);
        assertThat(progress.runId()).isEqualTo("77");
        assertThat(progress.result().prUrl()).isEqualTo("https://github.com/CaioMC/app/pull/13");
        assertThat(progress.result().changedFiles()).containsExactly("src/A.java");
        assertThat(progress.result().verification()).isEqualTo("bash .agent/verify.sh terminou com código 0");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static byte[] zip(String name, String content) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
