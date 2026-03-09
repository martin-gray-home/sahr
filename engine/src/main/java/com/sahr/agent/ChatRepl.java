package com.sahr.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Map;

public final class ChatRepl {
    private final SahrAgent agent;
    private final BufferedReader reader;
    private final PrintStream out;
    private final boolean attentionDebug;
    private final int attentionTopN;

    public ChatRepl(SahrAgent agent, InputStream input, PrintStream out) {
        this.agent = agent;
        this.reader = new BufferedReader(new InputStreamReader(input));
        this.out = out;
        this.attentionDebug = Boolean.parseBoolean(System.getProperty("sahr.repl.attentionDebug", "false"));
        this.attentionTopN = parseTopN(System.getProperty("sahr.repl.attentionTopN", "3"));
    }

    public void run() throws IOException {
        out.println("SAHR REPL ready. Type 'exit' to quit.");
        while (true) {
            out.print("> ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                break;
            }
            if (trimmed.isEmpty()) {
                continue;
            }
            String response = agent.handle(trimmed);
            out.println(response);
            if (attentionDebug) {
                printAttentionDebug();
            }
        }
    }

    private int parseTopN(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return Math.max(1, Math.min(10, value));
        } catch (NumberFormatException ex) {
            return 3;
        }
    }

    private void printAttentionDebug() {
        agent.trace().ifPresent(trace -> {
            if (trace.entries().isEmpty()) {
                return;
            }
            int lastIndex = trace.entries().size() - 1;
            var entry = trace.entries().get(lastIndex);
            var candidates = entry.candidates();
            if (candidates.isEmpty()) {
                return;
            }
            out.println("-- attention top " + Math.min(attentionTopN, candidates.size()) + " --");
            for (int i = 0; i < Math.min(attentionTopN, candidates.size()); i++) {
                var candidate = candidates.get(i);
                out.println(formatAttentionCandidate(i + 1, candidate));
            }
        });
    }

    private String formatAttentionCandidate(int rank, com.sahr.core.ReasoningCandidate candidate) {
        Map<String, Double> breakdown = candidate.scoreBreakdown();
        String base = rank + ". " + candidate.type() + " " + candidate.payload();
        String scores = String.format(
                " head=%.3f query=%.3f final=%.3f",
                candidate.headScore(),
                candidate.queryMatchScore(),
                candidate.score()
        );
        String attention = String.format(
                " entity=%.3f relation=%.3f type=%.3f",
                breakdown.getOrDefault("attention_entity_match", 0.0),
                breakdown.getOrDefault("attention_relation_match", 0.0),
                breakdown.getOrDefault("attention_type_match", 0.0)
        );
        return base + scores + attention + " producedBy=" + candidate.producedBy();
    }
}
