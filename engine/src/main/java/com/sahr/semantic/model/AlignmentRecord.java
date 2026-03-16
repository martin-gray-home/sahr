package com.sahr.semantic.model;

import java.util.Objects;

public record AlignmentRecord(
        SemanticSourceReference source,
        String canonicalFamilyId,
        AlignmentConfidence confidence,
        InferencePolicy policy
) {
    public AlignmentRecord {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(canonicalFamilyId, "canonicalFamilyId");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(policy, "policy");
    }
}
