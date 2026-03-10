package com.sahr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

final class HeadExecutor {
    private static final Logger logger = Logger.getLogger(HeadExecutor.class.getName());

    private final List<SymbolicAttentionHead> heads;
    private final SymbolicAttentionScorer attentionScorer;

    HeadExecutor(List<SymbolicAttentionHead> heads, SymbolicAttentionScorer attentionScorer) {
        this.heads = new ArrayList<>(heads);
        this.attentionScorer = attentionScorer == null ? new SymbolicAttentionScorer() : attentionScorer;
    }

    List<ReasoningCandidate> execute(HeadContext context) {
        List<ReasoningCandidate> results = new ArrayList<>();
        for (SymbolicAttentionHead head : heads) {
            logger.fine(() -> head.explain(context));
            List<ReasoningCandidate> headResults = head.evaluate(context);
            for (ReasoningCandidate candidate : headResults) {
                SymbolicAttentionScorer.QueryMatchResult match = attentionScorer.score(context, candidate);
                double rawScore = clamp(candidate.headScore() * match.queryMatchScore());
                ReasoningCandidate scored = candidate.withAttentionScores(
                        match.queryMatchScore(),
                        rawScore,
                        match.breakdown(candidate.headScore(), rawScore)
                );
                results.add(scored);
            }
            logger.fine(() -> "Head " + head.getName() + " produced " + headResults.size() + " candidates");
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
}
