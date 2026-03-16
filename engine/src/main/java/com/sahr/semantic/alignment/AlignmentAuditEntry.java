package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticSourceReference;

import java.util.Objects;

public record AlignmentAuditEntry(
        SemanticSourceReference source,
        String targetFamilyId,
        AlignmentConfidence confidence,
        String rationale
) {
    public AlignmentAuditEntry {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetFamilyId, "targetFamilyId");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(rationale, "rationale");
    }
}
