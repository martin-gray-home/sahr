package com.sahr.agent;

import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.ReasoningTraceEntry;
import com.sahr.core.RelationAssertion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CommandProcessor {
    private static final int DEFAULT_EXPLAIN_DEPTH = 10;
    private final SahrAgent agent;

    public CommandProcessor(SahrAgent agent) {
        this.agent = agent;
    }

    public CommandResult handle(String input) {
        if (input == null || !input.startsWith(":")) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.length() <= 1) {
            return new CommandResult(false, "Unknown command. Type :help for a list of commands.");
        }
        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            return new CommandResult(false, "Unknown command. Type :help for a list of commands.");
        }
        String command = tokens.get(0).substring(1).toLowerCase(Locale.ROOT);
        List<String> args = tokens.subList(1, tokens.size());

        return switch (command) {
            case "help" -> new CommandResult(false, helpText());
            case "exit", "quit" -> new CommandResult(true, "Bye.");
            case "reset" -> {
                agent.resetWorkingMemory();
                yield new CommandResult(false, "Working memory reset.");
            }
            case "explain" -> explain(args);
            default -> new CommandResult(false, "Unknown command. Type :help for a list of commands.");
        };
    }

    private CommandResult explain(List<String> args) {
        ExplainOptions options = parseExplainOptions(args);
        if (options.error != null) {
            return new CommandResult(false, options.error + "\n" + explainUsage());
        }
        Optional<ReasoningTraceEntry> lastEntry = agent.lastTraceEntry();
        if (lastEntry.isEmpty()) {
            return new CommandResult(false, "No reasoning trace yet. Ask a question first.");
        }
        return new CommandResult(false, formatExplain(lastEntry.get(), options));
    }

    private String formatExplain(ReasoningTraceEntry entry, ExplainOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append("Last query: ").append(formatQuery(entry.query())).append('\n');
        sb.append("Winner: ").append(formatCandidate(entry.winner(), false)).append('\n');

        int depth = Math.max(0, options.depth);
        List<ReasoningCandidate> candidates = entry.candidates();
        sb.append("Candidates: ").append(Math.min(depth, candidates.size()))
                .append(" of ").append(candidates.size()).append('\n');
        for (int i = 0; i < Math.min(depth, candidates.size()); i++) {
            ReasoningCandidate candidate = candidates.get(i);
            sb.append(i + 1).append(". ").append(formatCandidate(candidate, options.verbose)).append('\n');
            if (options.verbose) {
                sb.append("evidence: ").append(formatEvidence(candidate.evidence())).append('\n');
                sb.append("score_breakdown: ").append(formatBreakdown(candidate.scoreBreakdown())).append('\n');
            }
        }

        if (options.memory) {
            WorkingMemorySnapshot snapshot = agent.workingMemorySnapshot();
            sb.append("Working memory").append('\n');
            sb.append("active_entities: ").append(formatEntities(snapshot.activeEntities())).append('\n');
            sb.append("recent_assertions: ").append(formatAssertions(snapshot.recentAssertions())).append('\n');
            sb.append("goal_stack: ").append(formatGoals(snapshot.goalStack())).append('\n');
        }

        if (options.heads) {
            List<String> descriptions = agent.describeHeads(entry.query());
            sb.append("Heads:").append('\n');
            for (String description : descriptions) {
                sb.append("- ").append(description).append('\n');
            }
        }

        return sb.toString().trim();
    }

    private String formatCandidate(ReasoningCandidate candidate, boolean verbose) {
        String base = candidate.type() + " " + candidate.payload();
        String scores = String.format(
                Locale.ROOT,
                " score=%.3f head=%.3f query=%.3f depth=%d by=%s",
                candidate.score(),
                candidate.headScore(),
                candidate.queryMatchScore(),
                candidate.inferenceDepth(),
                candidate.producedBy()
        );
        if (!verbose) {
            return base + scores;
        }
        return base + scores;
    }

    private String formatQuery(QueryGoal query) {
        if (query == null) {
            return "unknown";
        }
        return switch (query.type()) {
            case WHERE -> "WHERE entity=" + safe(query.entityType())
                    + " expectedRange=" + safe(query.expectedRange());
            case RELATION -> "RELATION subject=" + safe(query.subject())
                    + " predicate=" + safe(query.predicate())
                    + " object=" + safe(query.object())
                    + " expectedType=" + safe(query.expectedType());
            case YESNO -> "YESNO subject=" + safe(query.subject())
                    + " predicate=" + safe(query.predicate())
                    + " object=" + safe(query.object());
            case ATTRIBUTE -> "ATTRIBUTE subject=" + safe(query.subject())
                    + " attribute=" + safe(query.attribute());
            case COUNT -> "COUNT subject=" + safe(query.subject())
                    + " predicate=" + safe(query.predicate())
                    + " object=" + safe(query.object())
                    + " expectedType=" + safe(query.expectedType())
                    + " modifier=" + safe(query.modifier());
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private String formatEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "none";
        }
        return String.join(" | ", evidence);
    }

    private String formatBreakdown(Map<String, Double> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(breakdown.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey())
                    .append("=")
                    .append(String.format(Locale.ROOT, "%.3f", entry.getValue()));
        }
        return sb.toString();
    }

    private String formatEntities(List<?> entities) {
        if (entities == null || entities.isEmpty()) {
            return "none";
        }
        List<String> values = new ArrayList<>();
        for (Object entity : entities) {
            values.add(String.valueOf(entity));
        }
        return String.join(", ", values);
    }

    private String formatAssertions(List<RelationAssertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return "none";
        }
        List<String> values = new ArrayList<>();
        for (RelationAssertion assertion : assertions) {
            values.add(assertion.subject() + " " + assertion.predicate() + " " + assertion.object());
        }
        return String.join(" | ", values);
    }

    private String formatGoals(List<QueryGoal> goals) {
        if (goals == null || goals.isEmpty()) {
            return "none";
        }
        List<String> values = new ArrayList<>();
        for (QueryGoal goal : goals) {
            values.add(formatQuery(goal));
        }
        return String.join(" | ", values);
    }

    private String safe(String value) {
        return value == null ? "none" : value;
    }

    private List<String> tokenize(String input) {
        String[] split = input.trim().split("\\s+");
        List<String> tokens = new ArrayList<>(split.length);
        for (String token : split) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private ExplainOptions parseExplainOptions(List<String> args) {
        int depth = DEFAULT_EXPLAIN_DEPTH;
        boolean verbose = false;
        boolean memory = false;
        boolean heads = false;
        int index = 0;
        while (index < args.size()) {
            String token = args.get(index);
            if (token.startsWith("--depth=")) {
                String value = token.substring("--depth=".length());
                Integer parsed = parseDepth(value);
                if (parsed == null) {
                    return ExplainOptions.error("Invalid depth value: " + value);
                }
                depth = parsed;
            } else if (token.equals("--depth")) {
                if (index + 1 >= args.size()) {
                    return ExplainOptions.error("Missing value for --depth.");
                }
                String value = args.get(index + 1);
                Integer parsed = parseDepth(value);
                if (parsed == null) {
                    return ExplainOptions.error("Invalid depth value: " + value);
                }
                depth = parsed;
                index++;
            } else if (token.equals("--verbose")) {
                verbose = true;
            } else if (token.equals("--memory")) {
                memory = true;
            } else if (token.equals("--head") || token.equals("--heads")) {
                heads = true;
            } else {
                return ExplainOptions.error("Unknown option: " + token);
            }
            index++;
        }
        return new ExplainOptions(depth, verbose, memory, heads, null);
    }

    private Integer parseDepth(String raw) {
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < 0) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String helpText() {
        return String.join("\n",
                "Commands:",
                ":help",
                ":exit | :quit",
                ":reset",
                ":explain [--depth N] [--verbose] [--memory] [--heads]",
                "",
                "Examples:",
                ":explain",
                ":explain --depth 5 --verbose",
                ":explain --memory --heads"
        );
    }

    private String explainUsage() {
        return "Usage: :explain [--depth N] [--verbose] [--memory] [--heads]";
    }

    public record CommandResult(boolean shouldExit, String output) {
    }

    private record ExplainOptions(int depth, boolean verbose, boolean memory, boolean heads, String error) {
        static ExplainOptions error(String message) {
            return new ExplainOptions(DEFAULT_EXPLAIN_DEPTH, false, false, false, message);
        }
    }
}
