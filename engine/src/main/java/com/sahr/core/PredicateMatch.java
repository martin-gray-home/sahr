package com.sahr.core;

import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.Optional;

public record PredicateMatch(String predicate, PredicateMatchType type, InferencePolicyStrength policyStrength) {
    public boolean matchesSubject(RelationAssertion assertion, SymbolId subject) {
        return isSwapped() ? assertion.object().equals(subject) : assertion.subject().equals(subject);
    }

    public boolean matchesObject(RelationAssertion assertion, SymbolId object) {
        return isSwapped() ? assertion.subject().equals(object) : assertion.object().equals(object);
    }

    public boolean isSwapped() {
        return type != PredicateMatchType.DIRECT;
    }

    public boolean isInverse() {
        return type == PredicateMatchType.INVERSE;
    }

    public double queryMatchScore() {
        if (policyStrength == null) {
            return type == PredicateMatchType.INVERSE ? 0.9 : 1.0;
        }
        return switch (policyStrength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
    }

    public Optional<Double> policyStrengthScore() {
        if (policyStrength == null) {
            return Optional.empty();
        }
        return switch (policyStrength) {
            case HARD -> Optional.of(1.0);
            case SOFT -> Optional.of(0.9);
            case RANKING_HINT -> Optional.of(0.6);
            case DISABLED -> Optional.of(0.0);
        };
    }
}
