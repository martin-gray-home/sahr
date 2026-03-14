package com.sahr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;

final class HeadExecutor {
    private static final Logger logger = Logger.getLogger(HeadExecutor.class.getName());

    private final List<SymbolicAttentionHead> heads;
    private final SymbolicAttentionScorer attentionScorer;
    private final boolean parallel;
    private final boolean timing;

    HeadExecutor(List<SymbolicAttentionHead> heads, SymbolicAttentionScorer attentionScorer) {
        this.heads = new ArrayList<>(heads);
        this.attentionScorer = attentionScorer == null ? new SymbolicAttentionScorer() : attentionScorer;
        this.parallel = Boolean.parseBoolean(System.getProperty("sahr.heads.parallel", "true"));
        this.timing = Boolean.parseBoolean(System.getProperty("sahr.heads.timing", "false"));
    }

    List<ReasoningCandidate> execute(HeadContext context) {
        List<ReasoningCandidate> results = new ArrayList<>();
        for (SymbolicAttentionHead head : heads) {
            logger.fine(() -> head.explain(context));
        }

        HeadOutcome[] outcomes = new HeadOutcome[heads.size()];
        String[] timingLines = timing ? new String[heads.size()] : null;
        long totalStart = timing ? System.nanoTime() : 0L;
        IntStream stream = IntStream.range(0, heads.size());
        if (parallel) {
            stream = stream.parallel();
        }
        stream.forEach(index -> outcomes[index] = evaluateHead(heads.get(index), context));

        for (int i = 0; i < outcomes.length; i++) {
            HeadOutcome outcome = outcomes[i];
            if (outcome == null) {
                continue;
            }
            results.addAll(outcome.scoredCandidates);
            logger.fine(() -> "Head " + outcome.headName + " produced " + outcome.rawCount + " candidates");
            if (timingLines != null) {
                timingLines[i] = formatTimingLine(outcome);
            }
        }
        if (timingLines != null) {
            long totalMs = nanosToMillis(System.nanoTime() - totalStart);
            for (String line : timingLines) {
                if (line != null) {
                    System.out.println(line);
                }
            }
            System.out.println("head_timing_total_ms=" + totalMs);
        }
        return results;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private HeadOutcome evaluateHead(SymbolicAttentionHead head, HeadContext context) {
        long start = timing ? System.nanoTime() : 0L;
        List<ReasoningCandidate> headResults = head.evaluate(context);
        List<ReasoningCandidate> scored = new ArrayList<>(headResults.size());
        for (ReasoningCandidate candidate : headResults) {
            SymbolicAttentionScorer.QueryMatchResult match = attentionScorer.score(context, candidate);
            double rawScore = clamp(candidate.headScore() * match.queryMatchScore());
            ReasoningCandidate scoredCandidate = candidate.withAttentionScores(
                    match.queryMatchScore(),
                    rawScore,
                    match.breakdown(candidate.headScore(), rawScore)
            );
            scored.add(scoredCandidate);
        }
        long durationMs = timing ? nanosToMillis(System.nanoTime() - start) : 0L;
        return new HeadOutcome(head.getName(), headResults.size(), scored, durationMs);
    }

    private String formatTimingLine(HeadOutcome outcome) {
        return "head_timing name=" + outcome.headName
                + " ms=" + outcome.durationMs
                + " candidates=" + outcome.rawCount;
    }

    private long nanosToMillis(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }

    private static final class HeadOutcome {
        private final String headName;
        private final int rawCount;
        private final List<ReasoningCandidate> scoredCandidates;
        private final long durationMs;

        private HeadOutcome(String headName, int rawCount, List<ReasoningCandidate> scoredCandidates, long durationMs) {
            this.headName = headName;
            this.rawCount = rawCount;
            this.scoredCandidates = scoredCandidates;
            this.durationMs = durationMs;
        }
    }
}
