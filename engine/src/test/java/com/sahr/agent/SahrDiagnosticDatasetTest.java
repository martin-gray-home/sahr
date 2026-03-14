package com.sahr.agent;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrDiagnosticDatasetTest {
    @Test
    void diagnosticDatasetReport() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("sahr.diagnostic.report"),
                "Enable diagnostic report with -Dsahr.diagnostic.report=true");
        DiagnosticTestSupport.DiagnosticDataset dataset = DiagnosticTestSupport.loadDataset();
        SahrAgent agent = DiagnosticTestSupport.buildFullAgent();
        ingestStatements(agent, dataset.statements());
        List<String> mismatches = new ArrayList<>();
        for (DiagnosticTestSupport.DiagnosticCase entry : dataset.cases()) {
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
        DiagnosticTestSupport.DiagnosticDataset dataset = DiagnosticTestSupport.loadDataset();
        SahrAgent agent = DiagnosticTestSupport.buildFullAgent();
        ingestStatements(agent, dataset.statements());
        List<String> failures = new ArrayList<>();
        for (DiagnosticTestSupport.DiagnosticCase entry : dataset.cases()) {
            String answer = agent.handle(entry.question());
            if (!matchesExpected(answer, entry.expectedAnswers())) {
                failures.add("Q" + entry.index() + " expected " + entry.expectedAnswers() + " but got: " + answer);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void diagnosticDatasetFullLog() throws IOException {
        Path logPath = diagnosticLogPath();
        boolean enabled = Boolean.getBoolean("sahr.diagnostic.full");
        if (!enabled) {
            String env = System.getenv("SAHR_DIAGNOSTIC_FULL");
            enabled = env != null && env.equalsIgnoreCase("true");
        }
        if (!enabled && logPath != null) {
            enabled = true;
        }
        Assumptions.assumeTrue(enabled,
                "Enable full diagnostic logs with -Dsahr.diagnostic.full=true or SAHR_DIAGNOSTIC_FULL=true");
        DiagnosticTestSupport.DiagnosticDataset dataset = DiagnosticTestSupport.loadDataset();
        SahrAgent agent = DiagnosticTestSupport.buildFullAgent();
        ingestStatements(agent, dataset.statements());
        CommandProcessor commandProcessor = new CommandProcessor(agent);
        java.io.BufferedWriter logWriter = null;
        try {
            if (logPath != null) {
                Path parent = logPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                logWriter = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8);
            }
            logLine(logWriter, "Diagnostic dataset full log:");
            for (DiagnosticTestSupport.DiagnosticCase entry : dataset.cases()) {
                String answer = agent.handle(entry.question());
                CommandProcessor.CommandResult explainResult = commandProcessor.handle(":explain --depth 5 --verbose");
                String reasoning = explainResult == null ? "No reasoning trace." : explainResult.output();
                logLine(logWriter, "Q" + entry.index() + ": " + entry.question());
                logLine(logWriter, "A" + entry.index() + ": " + answer);
                logLine(logWriter, "R" + entry.index() + ":");
                logBlock(logWriter, reasoning);
                logLine(logWriter, "---");
            }
        } finally {
            if (logWriter != null) {
                logWriter.flush();
                logWriter.close();
            }
        }
    }

    private Path diagnosticLogPath() {
        String path = System.getProperty("sahr.diagnostic.log.path");
        if (path == null || path.isBlank()) {
            path = System.getenv("SAHR_DIAGNOSTIC_LOG");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        return Path.of(path.trim());
    }

    private void logLine(java.io.BufferedWriter writer, String line) throws IOException {
        System.out.println(line);
        if (writer != null) {
            writer.write(line);
            writer.newLine();
        }
    }

    private void logBlock(java.io.BufferedWriter writer, String block) throws IOException {
        System.out.println(block);
        if (writer != null) {
            writer.write(block);
            writer.newLine();
        }
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
        String normalizedAnswer = DiagnosticTestSupport.normalize(answer);
        for (String expected : expectedAnswers) {
            List<String> keywords = DiagnosticTestSupport.extractKeywords(expected);
            if (keywords.isEmpty()) {
                continue;
            }
            if (!DiagnosticTestSupport.containsAll(normalizedAnswer, keywords)) {
                return false;
            }
        }
        return true;
    }
}
