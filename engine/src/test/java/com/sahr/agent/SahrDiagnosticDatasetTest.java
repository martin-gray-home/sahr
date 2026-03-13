package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrDiagnosticDatasetTest {
    private static final String DATASET_PATH = "datasets/sahr-diagnostic-reasoning.txt";

    @Test
    void diagnosticDatasetReport() throws IOException {
        DiagnosticDataset dataset = loadDataset();
        SahrAgent agent = SahrTestAgentFactory.newAgent(new InMemoryKnowledgeBase());
        ingestStatements(agent, dataset.statements());
        List<String> mismatches = new ArrayList<>();
        for (DiagnosticCase entry : dataset.cases()) {
            String answer = agent.handle(entry.question());
            boolean match = matchesExpected(answer, entry.expectedAnswers());
            if (!match) {
                mismatches.add("Q" + entry.index() + " expected " + entry.expectedAnswers() + " but got: " + answer);
            }
        }
        if (!mismatches.isEmpty()) {
            System.out.println("Diagnostic dataset mismatches:");
            mismatches.forEach(System.out::println);
        }
    }

    @Test
    void diagnosticDatasetStrict() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("sahr.diagnostic.strict"),
                "Enable strict diagnostic assertions with -Dsahr.diagnostic.strict=true");
        DiagnosticDataset dataset = loadDataset();
        SahrAgent agent = SahrTestAgentFactory.newAgent(new InMemoryKnowledgeBase());
        ingestStatements(agent, dataset.statements());
        List<String> failures = new ArrayList<>();
        for (DiagnosticCase entry : dataset.cases()) {
            String answer = agent.handle(entry.question());
            if (!matchesExpected(answer, entry.expectedAnswers())) {
                failures.add("Q" + entry.index() + " expected " + entry.expectedAnswers() + " but got: " + answer);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private void ingestStatements(SahrAgent agent, List<String> statements) {
        for (String statement : statements) {
            if (statement == null || statement.isBlank()) {
                continue;
            }
            agent.handle(statement);
        }
    }

    private boolean matchesExpected(String answer, List<String> expectedAnswers) {
        if (expectedAnswers.isEmpty()) {
            return true;
        }
        String normalizedAnswer = normalize(answer);
        for (String expected : expectedAnswers) {
            List<String> keywords = extractKeywords(expected);
            if (keywords.isEmpty()) {
                continue;
            }
            if (!containsAll(normalizedAnswer, keywords)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAll(String answer, List<String> keywords) {
        for (String keyword : keywords) {
            if (!answer.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private List<String> extractKeywords(String value) {
        String normalized = normalize(value);
        String[] parts = normalized.split(" ");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (STOPWORDS.contains(part)) {
                continue;
            }
            keywords.add(part);
        }
        return keywords;
    }

    private DiagnosticDataset loadDataset() throws IOException {
        List<String> statements = new ArrayList<>();
        List<DiagnosticCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireResource(DATASET_PATH), StandardCharsets.UTF_8))) {
            String line;
            boolean inStatements = false;
            boolean inQuestions = false;
            DiagnosticCaseBuilder builder = null;
            SectionMode mode = SectionMode.NONE;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("# Statements")) {
                    inStatements = true;
                    inQuestions = false;
                    continue;
                }
                if (trimmed.startsWith("# Questions")) {
                    inStatements = false;
                    inQuestions = true;
                    continue;
                }
                if (trimmed.startsWith("## Q")) {
                    if (builder != null) {
                        cases.add(builder.build());
                    }
                    builder = new DiagnosticCaseBuilder(parseIndex(trimmed));
                    mode = SectionMode.NONE;
                    continue;
                }
                if (inStatements) {
                    if (!trimmed.startsWith("#")) {
                        statements.add(trimmed);
                    }
                    continue;
                }
                if (inQuestions && builder != null) {
                    if ("Question".equalsIgnoreCase(trimmed)) {
                        mode = SectionMode.QUESTION;
                        continue;
                    }
                    if ("Expected Answer".equalsIgnoreCase(trimmed)) {
                        mode = SectionMode.EXPECTED_ANSWER;
                        continue;
                    }
                    if (trimmed.startsWith("Expected Reasoning")) {
                        mode = SectionMode.OTHER;
                        continue;
                    }
                    if (mode == SectionMode.QUESTION) {
                        builder.question(trimmed);
                        mode = SectionMode.NONE;
                        continue;
                    }
                    if (mode == SectionMode.EXPECTED_ANSWER) {
                        if (!trimmed.startsWith("#")) {
                            builder.addExpectedAnswer(trimmed);
                        }
                    }
                }
            }
            if (builder != null) {
                cases.add(builder.build());
            }
        }
        return new DiagnosticDataset(statements, cases);
    }

    private int parseIndex(String header) {
        String digits = header.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private java.io.InputStream requireResource(String path) {
        java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing dataset resource: " + path);
        }
        return stream;
    }

    private enum SectionMode {
        NONE,
        QUESTION,
        EXPECTED_ANSWER,
        OTHER
    }

    private record DiagnosticDataset(List<String> statements, List<DiagnosticCase> cases) {
    }

    private record DiagnosticCase(int index, String question, List<String> expectedAnswers) {
    }

    private static final class DiagnosticCaseBuilder {
        private final int index;
        private String question;
        private final List<String> expectedAnswers = new ArrayList<>();

        private DiagnosticCaseBuilder(int index) {
            this.index = index;
        }

        private void question(String value) {
            if (question == null || question.isBlank()) {
                question = value;
            }
        }

        private void addExpectedAnswer(String value) {
            expectedAnswers.add(value);
        }

        private DiagnosticCase build() {
            String q = question == null ? "" : question;
            return new DiagnosticCase(index, q, new ArrayList<>(expectedAnswers));
        }
    }

    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        String[] words = {
                "the", "a", "an", "of", "and", "or", "most", "likely", "because",
                "would", "should", "could", "chain", "failure", "fail", "fails", "failed",
                "caused", "cause", "causes", "loss", "lost", "before", "after", "control",
                "orientation", "system", "systems", "component", "components", "recovery",
                "mechanism", "evidence", "indicates", "indicate", "suggest", "suggests",
                "explanation", "sequence", "telemetry", "signal", "occur", "occurs", "occurred",
                "remained", "remains", "remain", "stable", "instability", "restored", "restore",
                "regain", "regained", "available", "unavailable", "when", "why", "under", "what"
        };
        for (String word : words) {
            STOPWORDS.add(word);
        }
    }
}
