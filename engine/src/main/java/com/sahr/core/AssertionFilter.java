package com.sahr.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class AssertionFilter {
    private final SymbolId subject;
    private final String predicate;
    private final SymbolId object;
    private final Set<AssertionLayer> layers;

    private AssertionFilter(SymbolId subject, String predicate, SymbolId object, Set<AssertionLayer> layers) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.layers = layers == null ? Set.of() : Collections.unmodifiableSet(layers);
    }

    public static AssertionFilter any() {
        return new AssertionFilter(null, null, null, Set.of());
    }

    public static AssertionFilter of(SymbolId subject, String predicate, SymbolId object, Set<AssertionLayer> layers) {
        Objects.requireNonNull(layers, "layers");
        return new AssertionFilter(subject, predicate, object, layers);
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

    public Set<AssertionLayer> layers() {
        return layers;
    }

    public boolean matches(AssertionRecord record) {
        if (record == null) {
            return false;
        }
        if (subject != null && !subject.equals(record.subject())) {
            return false;
        }
        if (predicate != null && !predicate.equals(record.predicate())) {
            return false;
        }
        if (object != null && !object.equals(record.object())) {
            return false;
        }
        if (!layers.isEmpty() && !layers.contains(record.layer())) {
            return false;
        }
        return true;
    }
}
