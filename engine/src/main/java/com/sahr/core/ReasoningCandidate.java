package com.sahr.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReasoningCandidate {
    private final CandidateType type;
    private final Object payload;
    private final double headScore;
    private final double queryMatchScore;
    private final double score;
    private final String producedBy;
    private final List<String> evidence;
    private final Map<String, Double> scoreBreakdown;
    private final int inferenceDepth;

    public ReasoningCandidate(
            CandidateType type,
            Object payload,
            double headScore,
            String producedBy,
            List<String> evidence,
            Map<String, Double> scoreBreakdown,
            int inferenceDepth
    ) {
        this(type, payload, headScore, 1.0, headScore, producedBy, evidence, scoreBreakdown, inferenceDepth);
    }

    private ReasoningCandidate(
            CandidateType type,
            Object payload,
            double headScore,
            double queryMatchScore,
            double score,
            String producedBy,
            List<String> evidence,
            Map<String, Double> scoreBreakdown,
            int inferenceDepth
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.payload = payload;
        this.headScore = headScore;
        this.queryMatchScore = queryMatchScore;
        this.score = score;
        this.producedBy = Objects.requireNonNull(producedBy, "producedBy");
        this.evidence = Collections.unmodifiableList(Objects.requireNonNull(evidence, "evidence"));
        this.scoreBreakdown = Collections.unmodifiableMap(Objects.requireNonNull(scoreBreakdown, "scoreBreakdown"));
        this.inferenceDepth = inferenceDepth;
    }

    public CandidateType type() {
        return type;
    }

    public Object payload() {
        return payload;
    }

    public double score() {
        return score;
    }

    public double headScore() {
        return headScore;
    }

    public double queryMatchScore() {
        return queryMatchScore;
    }

    public String producedBy() {
        return producedBy;
    }

    public List<String> evidence() {
        return evidence;
    }

    public Map<String, Double> scoreBreakdown() {
        return scoreBreakdown;
    }

    public int inferenceDepth() {
        return inferenceDepth;
    }

    public ReasoningCandidate withAttentionScores(double queryMatchScore,
                                                  double finalScore,
                                                  Map<String, Double> attentionBreakdown) {
        Map<String, Double> merged = new java.util.HashMap<>(scoreBreakdown);
        merged.putAll(attentionBreakdown);
        return new ReasoningCandidate(
                type,
                payload,
                headScore,
                queryMatchScore,
                finalScore,
                producedBy,
                evidence,
                merged,
                inferenceDepth
        );
    }
}
