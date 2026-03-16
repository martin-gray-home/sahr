package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentOutput;

import java.util.Objects;

public record OwlAlignmentResult(
        AlignmentOutput alignment,
        OwlImportReport report
) {
    public OwlAlignmentResult {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(report, "report");
    }
}
