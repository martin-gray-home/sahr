package com.sahr.semantic.model;

import java.util.List;
import java.util.Objects;

public record SemanticNode(
        String id,
        String label,
        String familyId,
        SemanticNodeType type,
        AlignmentConfidence confidence,
        List<AlignmentRecord> alignments,
        List<SemanticSourceReference> sources
) {
    public SemanticNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(alignments, "alignments");
        Objects.requireNonNull(sources, "sources");
    }
}
