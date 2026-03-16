package com.sahr.semantic.importer;

import java.util.Objects;

public record OwlImportReport(
        int classCount,
        int objectPropertyCount,
        int classesMissingLabels,
        int objectPropertiesMissingLabels
) {
    public OwlImportReport {
        if (classCount < 0 || objectPropertyCount < 0 || classesMissingLabels < 0 || objectPropertiesMissingLabels < 0) {
            throw new IllegalArgumentException("Import report counts must be non-negative.");
        }
    }
}
