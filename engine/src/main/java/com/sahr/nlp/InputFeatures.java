package com.sahr.nlp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class InputFeatures {
    private final Set<String> features;
    private final String raw;
    private final java.util.List<String> tokens;

    public InputFeatures(Set<String> features, String raw, java.util.List<String> tokens) {
        this.features = Collections.unmodifiableSet(features == null ? Set.of() : new HashSet<>(features));
        this.raw = raw == null ? "" : raw;
        this.tokens = tokens == null ? java.util.List.of() : java.util.List.copyOf(tokens);
    }

    public boolean has(String feature) {
        return features.contains(feature);
    }

    public Set<String> features() {
        return features;
    }

    public String raw() {
        return raw;
    }

    public java.util.List<String> tokens() {
        return tokens;
    }
}
