package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AlignmentSummary(Map<AlignmentConfidence, Integer> countsByConfidence) {
    public AlignmentSummary {
        Objects.requireNonNull(countsByConfidence, "countsByConfidence");
    }

    public static AlignmentSummary fromEntries(List<AlignmentAuditEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        Map<AlignmentConfidence, Integer> counts = new EnumMap<>(AlignmentConfidence.class);
        for (AlignmentConfidence confidence : AlignmentConfidence.values()) {
            counts.put(confidence, 0);
        }
        for (AlignmentAuditEntry entry : entries) {
            counts.computeIfPresent(entry.confidence(), (key, value) -> value + 1);
        }
        return new AlignmentSummary(counts);
    }
}
