package com.sahr.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class SahrReasoner {
    private static final Logger logger = Logger.getLogger(SahrReasoner.class.getName());
    private static final String ONTOLOGY_SUPPORT_KEY = "ontology_support";

    private final List<SymbolicAttentionHead> heads;
    private final SymbolicAttentionScorer attentionScorer;

    public SahrReasoner(List<SymbolicAttentionHead> heads) {
        this(heads, new SymbolicAttentionScorer());
    }

    public SahrReasoner(List<SymbolicAttentionHead> heads, SymbolicAttentionScorer attentionScorer) {
        this.heads = new ArrayList<>(heads);
        this.attentionScorer = attentionScorer == null ? new SymbolicAttentionScorer() : attentionScorer;
    }

    public List<ReasoningCandidate> reason(HeadContext context) {
        List<ReasoningCandidate> results = new ArrayList<>();
        for (SymbolicAttentionHead head : heads) {
            List<ReasoningCandidate> headResults = head.evaluate(context);
            for (ReasoningCandidate candidate : headResults) {
                SymbolicAttentionScorer.QueryMatchResult match = attentionScorer.score(context, candidate);
                double finalScore = clamp(candidate.headScore() * match.queryMatchScore());
                ReasoningCandidate scored = candidate.withAttentionScores(
                        match.queryMatchScore(),
                        finalScore,
                        match.breakdown(candidate.headScore(), finalScore)
                );
                results.add(scored);
            }
            logger.fine(() -> "Head " + head.getName() + " produced " + headResults.size() + " candidates");
        }
        results.sort(candidateComparator());
        return results;
    }

    public Optional<ReasoningCandidate> selectWinner(HeadContext context) {
        List<ReasoningCandidate> results = reason(context);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    private Comparator<ReasoningCandidate> candidateComparator() {
        return Comparator
                .comparingDouble(ReasoningCandidate::score).reversed()
                .thenComparingDouble(candidate -> candidate.scoreBreakdown().getOrDefault(ONTOLOGY_SUPPORT_KEY, 0.0)).reversed()
                .thenComparingInt(candidate -> candidate.evidence().size()).reversed()
                .thenComparingInt(ReasoningCandidate::inferenceDepth)
                .thenComparing(ReasoningCandidate::producedBy);
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
