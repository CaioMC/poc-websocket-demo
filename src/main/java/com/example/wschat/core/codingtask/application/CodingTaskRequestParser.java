package com.example.wschat.core.codingtask.application;

import java.util.regex.Pattern;

/**
 * Interpreta o texto que o usuário escreveu depois de {@code /codificar}.
 *
 * Formatos aceitos:
 * <pre>
 * Criar endpoint GET /api/health que responde {"status":"UP"}
 * repo=CaioMC/outro-repo Criar endpoint GET /api/health
 * </pre>
 * A primeira linha vira o título da issue; o texto completo vira a descrição.
 */
final class CodingTaskRequestParser {

    private static final Pattern REPO_PREFIX = Pattern.compile("^\\s*repo=([\\w.-]+/[\\w.-]+)\\s+", Pattern.DOTALL);
    private static final int MAX_TITLE_LENGTH = 80;

    private CodingTaskRequestParser() {
    }

    record ParsedRequest(String repository, String title, String description) {
    }

    static ParsedRequest parse(String rawRequest, String defaultRepository) {
        var text = rawRequest == null ? "" : rawRequest.strip();
        var repository = defaultRepository;

        var matcher = REPO_PREFIX.matcher(text);
        if (matcher.find()) {
            repository = matcher.group(1);
            text = text.substring(matcher.end()).strip();
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Descreva o que deve ser implementado. Ex.: /codificar Criar endpoint GET /api/health");
        }

        var firstLine = text.lines().findFirst().orElse(text).strip();
        var title = firstLine.length() > MAX_TITLE_LENGTH
            ? firstLine.substring(0, MAX_TITLE_LENGTH - 3).strip() + "..."
            : firstLine;
        return new ParsedRequest(repository, title, text);
    }
}
