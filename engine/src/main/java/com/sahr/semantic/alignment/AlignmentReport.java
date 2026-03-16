package com.sahr.semantic.alignment;

import java.util.List;
import java.util.Objects;

public record AlignmentReport(
        List<AlignmentAuditEntry> entries,
        AlignmentSummary summary
) {
    public AlignmentReport {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(summary, "summary");
    }
}
