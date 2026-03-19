package com.sahr.core;

import java.util.List;
import java.util.Objects;

public final class RuleFrame {
    private final String variable;
    private final List<RuleAtom> antecedents;
    private final RuleAtom consequent;
    private final double confidence;

    public RuleFrame(String variable, List<RuleAtom> antecedents, RuleAtom consequent, double confidence) {
        this.variable = Objects.requireNonNull(variable, "variable");
        this.antecedents = List.copyOf(Objects.requireNonNull(antecedents, "antecedents"));
        if (this.antecedents.isEmpty()) {
            throw new IllegalArgumentException("antecedents must not be empty");
        }
        this.consequent = Objects.requireNonNull(consequent, "consequent");
        this.confidence = confidence;
    }

    public String variable() {
        return variable;
    }

    public List<RuleAtom> antecedents() {
        return antecedents;
    }

    public RuleAtom consequent() {
        return consequent;
    }

    public double confidence() {
        return confidence;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("rule(");
        List<String> variables = RuleFrames.variables(this);
        if (!variables.isEmpty()) {
            builder.append("forall ").append(String.join(", ", variables)).append(": ");
        }
        for (int i = 0; i < antecedents.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(antecedents.get(i));
        }
        builder.append(" -> ").append(consequent).append(")");
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuleFrame)) {
            return false;
        }
        RuleFrame that = (RuleFrame) other;
        return variable.equals(that.variable)
                && antecedents.equals(that.antecedents)
                && consequent.equals(that.consequent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, antecedents, consequent);
    }
}
