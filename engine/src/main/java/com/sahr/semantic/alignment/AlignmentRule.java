package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;

import java.util.Objects;

public record AlignmentRule(
        String targetFamilyId,
        AlignmentConfidence confidence,
        String rationale
) {
    public AlignmentRule {
        Objects.requireNonNull(targetFamilyId, "targetFamilyId");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(rationale, "rationale");
    }
}
