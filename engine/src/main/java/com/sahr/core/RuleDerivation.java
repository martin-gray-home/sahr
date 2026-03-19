package com.sahr.core;

import java.util.List;
import java.util.Objects;

public final class RuleDerivation {
    private final RelationAssertion assertion;
    private final List<String> evidence;
    private final List<String> supportingAssertionIds;
    private final double ruleConfidence;
    private final double evidenceConfidence;
    private final int inferenceDepth;

    public RuleDerivation(RelationAssertion assertion,
                          List<String> evidence,
                          List<String> supportingAssertionIds,
                          double ruleConfidence,
                          double evidenceConfidence,
                          int inferenceDepth) {
        this.assertion = Objects.requireNonNull(assertion, "assertion");
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        this.supportingAssertionIds = List.copyOf(Objects.requireNonNull(supportingAssertionIds, "supportingAssertionIds"));
        this.ruleConfidence = ruleConfidence;
        this.evidenceConfidence = evidenceConfidence;
        this.inferenceDepth = inferenceDepth;
    }

    public RelationAssertion assertion() {
        return assertion;
    }

    public List<String> evidence() {
        return evidence;
    }

    public List<String> supportingAssertionIds() {
        return supportingAssertionIds;
    }

    public double ruleConfidence() {
        return ruleConfidence;
    }

    public double evidenceConfidence() {
        return evidenceConfidence;
    }

    public int inferenceDepth() {
        return inferenceDepth;
    }

    public double score() {
        return Math.min(1.0, (ruleConfidence + evidenceConfidence) / 2.0);
    }
}
