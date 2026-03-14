package com.sahr.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SahrDiagnosticReplIntegrationTest {
    @Test
    void diagnosticDatasetReplSession() throws IOException {
        boolean enabled = Boolean.getBoolean("sahr.diagnostic.repl");
        if (!enabled) {
            String env = System.getenv("SAHR_DIAGNOSTIC_REPL");
            enabled = env != null && env.equalsIgnoreCase("true");
        }
        Path logPath = replLogPath();
        if (!enabled && logPath != null) {
            enabled = true;
        }
        Assumptions.assumeTrue(enabled,
                "Enable REPL diagnostic run with -Dsahr.diagnostic.repl=true or SAHR_DIAGNOSTIC_REPL=true");
        applyHeadTimingProperty();
        applyHandleTimingProperty();
        applySubgoalTimingProperty();
        long datasetStart = System.nanoTime();
        DiagnosticTestSupport.DiagnosticDataset dataset = DiagnosticTestSupport.loadDataset();
        long datasetLoaded = System.nanoTime();
        SahrAgent agent = DiagnosticTestSupport.buildFullAgent();
        long agentReady = System.nanoTime();

        Set<Integer> questionFilter = parseQuestionFilter();
        List<String> lines = new ArrayList<>();
        lines.addAll(dataset.statements());
        List<String> questions = new ArrayList<>();
        for (DiagnosticTestSupport.DiagnosticCase entry : dataset.cases()) {
            if (entry.question() == null || entry.question().isBlank()) {
                continue;
            }
            if (questionFilter != null && !questionFilter.contains(entry.index())) {
                continue;
            }
            questions.add(entry.question());
        }
        lines.addAll(questions);
        lines.add(":exit");

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8)) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            System.setOut(out);
            System.setErr(out);
            out.println("SAHR REPL integration timing:");
            out.println("dataset_load_ms=" + nanosToMillis(datasetLoaded - datasetStart));
            out.println("agent_init_ms=" + nanosToMillis(agentReady - datasetLoaded));
            out.println("total_init_ms=" + nanosToMillis(agentReady - datasetStart));
            if (questionFilter != null) {
                out.println("question_filter=" + questionFilter);
            }
            out.println("---");
            CommandProcessor commandProcessor = new CommandProcessor(agent);
            int index = 0;
            try {
                for (String line : lines) {
                    index++;
                    long lineStart = System.nanoTime();
                    String trimmed = line.trim();
                    CommandProcessor.CommandResult commandResult = commandProcessor.handle(trimmed);
                    if (commandResult != null) {
                        if (commandResult.output() != null && !commandResult.output().isBlank()) {
                            out.println(commandResult.output());
                        }
                        if (commandResult.shouldExit()) {
                            out.println("line_" + index + "_ms=" + nanosToMillis(System.nanoTime() - lineStart)
                                    + " kind=command value=" + trimmed);
                            break;
                        }
                        out.println("line_" + index + "_ms=" + nanosToMillis(System.nanoTime() - lineStart)
                                + " kind=command value=" + trimmed);
                        continue;
                    }
                    if (trimmed.isEmpty()) {
                        out.println("line_" + index + "_ms=" + nanosToMillis(System.nanoTime() - lineStart)
                                + " kind=empty");
                        continue;
                    }
                    String response = agent.handle(trimmed);
                    out.println(response);
                    String kind = index <= dataset.statements().size() ? "statement" : "question";
                    out.println("line_" + index + "_ms=" + nanosToMillis(System.nanoTime() - lineStart)
                            + " kind=" + kind);
                }
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }

        if (logPath != null) {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logPath, outputBuffer.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
    }

    private Path replLogPath() {
        String path = System.getProperty("sahr.diagnostic.repl.log.path");
        if (path == null || path.isBlank()) {
            path = System.getenv("SAHR_DIAGNOSTIC_REPL_LOG");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        return Path.of(path.trim());
    }

    private long nanosToMillis(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }

    private void applyHeadTimingProperty() {
        if (Boolean.getBoolean("sahr.heads.timing")) {
            return;
        }
        String env = System.getenv("SAHR_HEADS_TIMING");
        if (env != null && env.equalsIgnoreCase("true")) {
            System.setProperty("sahr.heads.timing", "true");
        }
    }

    private void applyHandleTimingProperty() {
        if (Boolean.getBoolean("sahr.handle.timing")) {
            return;
        }
        String env = System.getenv("SAHR_HANDLE_TIMING");
        if (env != null && env.equalsIgnoreCase("true")) {
            System.setProperty("sahr.handle.timing", "true");
        }
    }

    private void applySubgoalTimingProperty() {
        if (Boolean.getBoolean("sahr.subgoal.timing")) {
            return;
        }
        String env = System.getenv("SAHR_SUBGOAL_TIMING");
        if (env != null && env.equalsIgnoreCase("true")) {
            System.setProperty("sahr.subgoal.timing", "true");
        }
    }

    private Set<Integer> parseQuestionFilter() {
        String filter = System.getProperty("sahr.diagnostic.repl.questions");
        if (filter == null || filter.isBlank()) {
            filter = System.getenv("SAHR_DIAGNOSTIC_REPL_QUESTIONS");
        }
        if (filter == null || filter.isBlank()) {
            return null;
        }
        Set<Integer> indices = new java.util.HashSet<>();
        for (String part : filter.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String digits = trimmed.replaceAll("[^0-9]", "");
            if (digits.isBlank()) {
                continue;
            }
            indices.add(Integer.parseInt(digits));
        }
        if (indices.isEmpty()) {
            return null;
        }
        return indices;
    }
}
