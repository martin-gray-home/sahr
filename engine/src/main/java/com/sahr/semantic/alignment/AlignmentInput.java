package com.sahr.semantic.alignment;

import com.sahr.semantic.model.LexicalTrigger;
import com.sahr.semantic.model.SelectionalConstraint;
import com.sahr.semantic.model.SemanticNode;

import java.util.List;
import java.util.Objects;

public record AlignmentInput(
        List<SemanticNode> importedNodes,
        List<LexicalTrigger> triggers,
        List<SelectionalConstraint> constraints
) {
    public AlignmentInput {
        Objects.requireNonNull(importedNodes, "importedNodes");
        Objects.requireNonNull(triggers, "triggers");
        Objects.requireNonNull(constraints, "constraints");
    }
}
