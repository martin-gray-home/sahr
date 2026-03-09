package com.sahr.core;

import java.util.Objects;

public final class RelationAssertion {
    private final SymbolId subject;
    private final String predicate;
    private final SymbolId object;
    private final double confidence;

    public RelationAssertion(SymbolId subject, String predicate, SymbolId object, double confidence) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.object = Objects.requireNonNull(object, "object");
        this.confidence = confidence;
    }

    public SymbolId subject() {
        return subject;
    }

    public String predicate() {
        return predicate;
    }

    public SymbolId object() {
        return object;
    }

    public double confidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return subject + " " + predicate + " " + object;
    }
}
