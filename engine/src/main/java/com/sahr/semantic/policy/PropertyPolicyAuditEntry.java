package com.sahr.semantic.policy;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.List;
import java.util.Objects;

public record PropertyPolicyAuditEntry(
        String propertyIri,
        PropertyPolicyType type,
        InferencePolicyStrength strength,
        boolean enabled,
        AlignmentConfidence alignmentConfidence,
        List<String> targetPropertyIris,
        String rationale
) {
    public PropertyPolicyAuditEntry {
        Objects.requireNonNull(propertyIri, "propertyIri");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(alignmentConfidence, "alignmentConfidence");
        Objects.requireNonNull(targetPropertyIris, "targetPropertyIris");
        Objects.requireNonNull(rationale, "rationale");
    }
}
