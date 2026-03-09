package com.sahr.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
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
        List<ReasoningCandidate> normalized = applySoftmax(results);
        normalized.sort(candidateComparator());
        return normalized;
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

    private List<ReasoningCandidate> applySoftmax(List<ReasoningCandidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        double max = candidates.stream().mapToDouble(ReasoningCandidate::score).max().orElse(0.0);
        double sum = 0.0;
        double[] exp = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            exp[i] = Math.exp(candidates.get(i).score() - max);
            sum += exp[i];
        }
        if (sum == 0.0) {
            return candidates;
        }
        List<ReasoningCandidate> normalized = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ReasoningCandidate candidate = candidates.get(i);
            double weight = clamp(exp[i] / sum);
            Map<String, Double> extra = new java.util.HashMap<>(candidate.scoreBreakdown());
            extra.put("attention_raw_score", candidate.scoreBreakdown().getOrDefault("attention_final_score", candidate.score()));
            extra.put("attention_softmax", weight);
            normalized.add(candidate.withAttentionScores(candidate.queryMatchScore(), weight, extra));
        }
        return normalized;
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
