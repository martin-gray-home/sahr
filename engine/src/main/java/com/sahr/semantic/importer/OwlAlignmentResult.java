package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.policy.PropertyPolicyDecision;

import java.util.Objects;
import java.util.List;

public record OwlAlignmentResult(
        AlignmentOutput alignment,
        OwlImportReport report,
        List<PropertyPolicyDecision> propertyPolicyDecisions
) {
    public OwlAlignmentResult {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(propertyPolicyDecisions, "propertyPolicyDecisions");
    }
}
