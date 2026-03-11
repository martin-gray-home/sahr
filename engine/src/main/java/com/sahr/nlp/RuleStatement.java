package com.sahr.nlp;

import java.util.Objects;

public final class RuleStatement {
    private final Statement antecedent;
    private final Statement consequent;
    private final double confidence;

    public RuleStatement(Statement antecedent, Statement consequent, double confidence) {
        this.antecedent = Objects.requireNonNull(antecedent, "antecedent");
        this.consequent = Objects.requireNonNull(consequent, "consequent");
        this.confidence = confidence;
    }

    public Statement antecedent() {
        return antecedent;
    }

    public Statement consequent() {
        return consequent;
    }

    public double confidence() {
        return confidence;
    }
}
