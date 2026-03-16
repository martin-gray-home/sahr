package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentInput;

import java.util.Objects;

public record OwlImportResult(
        AlignmentInput input,
        OwlImportReport report
) {
    public OwlImportResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(report, "report");
    }
}
