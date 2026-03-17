package com.sahr.core;

import java.util.Objects;

public final class AssertionRecord {
    private final String id;
    private final SymbolId subject;
    private final String predicate;
    private final SymbolId object;
    private final double confidence;
    private final AssertionLayer layer;
    private final AssertionProvenance provenance;

    public AssertionRecord(String id,
                           SymbolId subject,
                           String predicate,
                           SymbolId object,
                           double confidence,
                           AssertionLayer layer,
                           AssertionProvenance provenance) {
        this.id = Objects.requireNonNull(id, "id");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.object = Objects.requireNonNull(object, "object");
        this.confidence = confidence;
        this.layer = Objects.requireNonNull(layer, "layer");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public String id() {
        return id;
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

    public AssertionLayer layer() {
        return layer;
    }

    public AssertionProvenance provenance() {
        return provenance;
    }

    public RelationAssertion toRelationAssertion() {
        return new RelationAssertion(subject, predicate, object, confidence);
    }

    public AssertionRecord withProvenance(AssertionProvenance provenance) {
        return new AssertionRecord(
                id,
                subject,
                predicate,
                object,
                confidence,
                layer,
                provenance
        );
    }

    @Override
    public String toString() {
        return subject + " " + predicate + " " + object + " [" + layer + "]";
    }
}
