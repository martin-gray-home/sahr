package com.sahr.semantic.policy;

import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PropertyPolicyReport(
        List<PropertyPolicyAuditEntry> entries,
        Map<InferencePolicyStrength, Integer> countsByStrength
) {
    public PropertyPolicyReport {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(countsByStrength, "countsByStrength");
    }
}
