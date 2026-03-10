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

    HeadExecutor(List<SymbolicAttentionHead> heads, SymbolicAttentionScorer attentionScorer) {
        this.heads = new ArrayList<>(heads);
        this.attentionScorer = attentionScorer == null ? new SymbolicAttentionScorer() : attentionScorer;
        this.parallel = Boolean.parseBoolean(System.getProperty("sahr.heads.parallel", "true"));
    }

    List<ReasoningCandidate> execute(HeadContext context) {
        List<ReasoningCandidate> results = new ArrayList<>();
        for (SymbolicAttentionHead head : heads) {
            logger.fine(() -> head.explain(context));
        }

        HeadOutcome[] outcomes = new HeadOutcome[heads.size()];
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
        return new HeadOutcome(head.getName(), headResults.size(), scored);
    }

    private static final class HeadOutcome {
        private final String headName;
        private final int rawCount;
        private final List<ReasoningCandidate> scoredCandidates;

        private HeadOutcome(String headName, int rawCount, List<ReasoningCandidate> scoredCandidates) {
            this.headName = headName;
            this.rawCount = rawCount;
            this.scoredCandidates = scoredCandidates;
        }
    }
}
