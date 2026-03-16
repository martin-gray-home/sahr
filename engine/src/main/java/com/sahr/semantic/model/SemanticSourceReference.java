package com.sahr.semantic.model;

import java.util.Objects;

public record SemanticSourceReference(
        String sourceSystem,
        String sourceId,
        String label,
        double confidence
) {
    public SemanticSourceReference {
        Objects.requireNonNull(sourceSystem, "sourceSystem");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(label, "label");
    }
}
