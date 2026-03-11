package com.sahr.nlp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class InputFeatures {
    private final Set<String> features;

    public InputFeatures(Set<String> features) {
        this.features = Collections.unmodifiableSet(features == null ? Set.of() : new HashSet<>(features));
    }

    public boolean has(String feature) {
        return features.contains(feature);
    }

    public Set<String> features() {
        return features;
    }
}
