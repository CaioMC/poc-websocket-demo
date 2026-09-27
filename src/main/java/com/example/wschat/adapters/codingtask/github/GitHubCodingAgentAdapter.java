package com.example.wschat.adapters.codingtask.github;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.wschat.adapters.codingtask.config.CodingAgentProperties;
import com.example.wschat.core.codingtask.domain.CodingTask;
import com.example.wschat.core.codingtask.domain.CodingTaskResult;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort;
import com.example.wschat.core.codingtask.domain.agent.CodingAgentPort.RunProgress.Phase;

/**
 * Implementação de {@link CodingAgentPort} com a REST API do GitHub.
 *
 * <ul>
 *   <li>{@code openIssue}: {@code POST /repos/{repo}/issues}</li>
 *   <li>{@code dispatch}: {@code POST /repos/{repo}/actions/workflows/{arquivo}/dispatches}</li>
 *   <li>{@code checkProgress}: acha a execução pelo nome ({@code coding-agent <id>}), lê o status
 *       e, no fim, baixa o artefato {@code agent-result} para ler o result.json.</li>
 * </ul>
 * Usa só o {@link HttpClient} do JDK e o Jackson que o projeto já tem: nenhuma dependência nova.
 */
@Component
public class GitHubCodingAgentAdapter implements CodingAgentPort {

    private static final String ARTIFACT_NAME = "agent-result";
    private static final String RUN_NAME_PREFIX = "coding-agent ";

    private final CodingAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        // Redirecionamentos (download de artefato) são seguidos à mão, sem o token: ver download().
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public GitHubCodingAgentAdapter(CodingAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public IssueRef openIssue(String repository, String title, String body) {
        var issue = this.callJson("POST", "/repos/" + repository + "/issues", Map.of("title", title, "body", body));
        return new IssueRef(issue.path("number").asInt(), issue.path("html_url").asText());
    }

    @Override
    public void dispatch(CodingTask task) {
        // Os valores de inputs do workflow_dispatch vão sempre como texto.
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("issue_number", String.valueOf(task.issueNumber()));
        inputs.put("request_id", task.id().value());
        inputs.put("base_branch", task.baseBranch());
        inputs.put("work_branch", task.workBranch());
        this.callJson("POST", this.workflowPath(task) + "/dispatches", Map.of("ref", task.baseBranch(), "inputs", inputs));
    }

    @Override
    public RunProgress checkProgress(CodingTask task) {
        var runId = task.runId() != null ? task.runId() : this.findRunId(task).orElse(null);
        if (runId == null) {
            return RunProgress.notFound();
        }

        var run = this.callJson("GET", "/repos/" + task.repository() + "/actions/runs/" + runId, null);
        var runUrl = run.path("html_url").asText(null);
        var status = run.path("status").asText("");
        if (!"completed".equals(status)) {
            var phase = "in_progress".equals(status) ? Phase.RUNNING : Phase.QUEUED;
            return new RunProgress(phase, runId, runUrl, null);
        }

        var phase = "success".equals(run.path("conclusion").asText()) ? Phase.SUCCEEDED : Phase.FAILED;
        var result = this.downloadResult(task.repository(), runId).orElse(null);
        return new RunProgress(phase, runId, runUrl, result);
    }

    // ------------------------------------------------------------------ execução e resultado

    private Optional<String> findRunId(CodingTask task) {
        var runs = this.callJson("GET", this.workflowPath(task) + "/runs?event=workflow_dispatch&per_page=30", null);
        var expectedName = RUN_NAME_PREFIX + task.id().value();
        for (JsonNode run : runs.path("workflow_runs")) {
            if (expectedName.equals(run.path("display_title").asText())) {
                return Optional.of(run.path("id").asText());
            }
        }
        return Optional.empty();
    }

    private Optional<CodingTaskResult> downloadResult(String repository, String runId) {
        var artifacts = this.callJson("GET",
            "/repos/" + repository + "/actions/runs/" + runId + "/artifacts?name=" + ARTIFACT_NAME, null);
        for (JsonNode artifact : artifacts.path("artifacts")) {
            if (ARTIFACT_NAME.equals(artifact.path("name").asText()) && !artifact.path("expired").asBoolean(false)) {
                var files = unzip(this.download(artifact.path("archive_download_url").asText()));
                var resultJson = files.get("result.json");
                return resultJson == null ? Optional.empty() : Optional.of(this.toResult(resultJson));
            }
        }
        return Optional.empty();
    }

    private CodingTaskResult toResult(byte[] resultJson) {
        try {
            JsonNode result = this.objectMapper.readTree(resultJson);
            List<String> changedFiles = new ArrayList<>();
            result.path("changedFiles").forEach(file -> changedFiles.add(file.asText()));

            JsonNode verification = result.path("verification");
            String verificationText = verification.path("executed").asBoolean(false)
                ? verification.path("command").asText() + " terminou com código " + verification.path("exitCode").asText()
                : "não executada";

            return new CodingTaskResult(
                result.path("status").asText(null),
                result.path("summary").asText(null),
                result.path("publish").path("prUrl").asText(null),
                changedFiles,
                verificationText
            );
        } catch (IOException e) {
            throw new UncheckedIOException("result.json inválido", e);
        }
    }

    // ------------------------------------------------------------------ HTTP

    private String workflowPath(CodingTask task) {
        return "/repos/" + task.repository() + "/actions/workflows/" + this.properties.workflowFile();
    }

    private JsonNode callJson(String method, String path, Object body) {
        var response = this.send(method, this.apiUrl() + path, body, true);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("GitHub respondeu HTTP " + response.statusCode() + " em "
                + method + " " + path + ": " + abbreviate(new String(response.body())));
        }
        try {
            return response.body().length == 0 ? this.objectMapper.createObjectNode() : this.objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * O GitHub responde o download de artefato com um redirecionamento para um storage com URL
     * assinada. Seguimos SEM o cabeçalho Authorization, que o storage recusaria.
     */
    private byte[] download(String url) {
        var response = this.send("GET", url, null, true);
        int hops = 0;
        while (response.statusCode() / 100 == 3 && hops++ < 5) {
            var location = response.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Redirecionamento sem Location"));
            response = this.send("GET", location, null, false);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Falha ao baixar artefato: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpResponse<byte[]> send(String method, String url, Object body, boolean authenticated) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
            if (authenticated) {
                request.header("Authorization", "Bearer " + this.token());
            }
            if (body != null) {
                request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(this.objectMapper.writeValueAsBytes(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return this.http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha de rede ao chamar o GitHub: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada ao GitHub interrompida", e);
        }
    }

    private String apiUrl() {
        var url = this.properties.github().apiUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String token() {
        var token = this.properties.github().token();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("token do GitHub não configurado. Defina a variável CODING_AGENT_GITHUB_TOKEN.");
        }
        return token;
    }

    private static Map<String, byte[]> unzip(byte[] zip) {
        Map<String, byte[]> files = new HashMap<>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (!entry.isDirectory()) {
                    var name = entry.getName();
                    files.put(name.substring(name.lastIndexOf('/') + 1), in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Artefato inválido", e);
        }
        return files;
    }

    private static String abbreviate(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
