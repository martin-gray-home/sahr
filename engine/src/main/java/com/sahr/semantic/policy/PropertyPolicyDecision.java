package com.sahr.semantic.policy;

import com.sahr.semantic.model.AlignmentConfidence;

import java.util.List;
import java.util.Objects;

public record PropertyPolicyDecision(
        String propertyIri,
        AlignmentConfidence confidence,
        List<PropertyPolicyRule> rules
) {
    public PropertyPolicyDecision {
        Objects.requireNonNull(propertyIri, "propertyIri");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(rules, "rules");
    }
}
