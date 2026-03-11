package com.sahr.core;

import java.util.Objects;

public final class RuleAssertion {
    private final RelationAssertion antecedent;
    private final RelationAssertion consequent;
    private final double confidence;

    public RuleAssertion(RelationAssertion antecedent, RelationAssertion consequent, double confidence) {
        this.antecedent = Objects.requireNonNull(antecedent, "antecedent");
        this.consequent = Objects.requireNonNull(consequent, "consequent");
        this.confidence = confidence;
    }

    public RelationAssertion antecedent() {
        return antecedent;
    }

    public RelationAssertion consequent() {
        return consequent;
    }

    public double confidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "rule(" + antecedent + " -> " + consequent + ")";
    }
}
