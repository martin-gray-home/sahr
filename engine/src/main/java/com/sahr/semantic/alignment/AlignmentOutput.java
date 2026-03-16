package com.sahr.semantic.alignment;

import com.sahr.semantic.model.LexicalTrigger;
import com.sahr.semantic.model.PropertySemantics;
import com.sahr.semantic.model.SelectionalConstraint;
import com.sahr.semantic.model.SemanticNode;

import java.util.List;
import java.util.Objects;

public record AlignmentOutput(
        List<SemanticNode> canonicalNodes,
        List<LexicalTrigger> canonicalTriggers,
        List<SelectionalConstraint> canonicalConstraints,
        List<PropertySemantics> propertySemantics,
        AlignmentReport report
) {
    public AlignmentOutput {
        Objects.requireNonNull(canonicalNodes, "canonicalNodes");
        Objects.requireNonNull(canonicalTriggers, "canonicalTriggers");
        Objects.requireNonNull(canonicalConstraints, "canonicalConstraints");
        Objects.requireNonNull(propertySemantics, "propertySemantics");
        Objects.requireNonNull(report, "report");
    }
}
