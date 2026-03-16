package com.sahr.semantic.model;

import java.util.List;
import java.util.Objects;

public record LexicalTrigger(
        String text,
        String normalizedText,
        String familyId,
        TriggerFamilyType familyType,
        AlignmentConfidence confidence,
        List<SemanticSourceReference> sources
) {
    public LexicalTrigger {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(normalizedText, "normalizedText");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(familyType, "familyType");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(sources, "sources");
    }
}
