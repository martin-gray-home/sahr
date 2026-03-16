package com.sahr.semantic.policy;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PropertyPolicyReportBuilder {
    private PropertyPolicyReportBuilder() {
    }

    public static PropertyPolicyReport fromDecisions(List<PropertyPolicyDecision> decisions) {
        Objects.requireNonNull(decisions, "decisions");
        List<PropertyPolicyAuditEntry> entries = new ArrayList<>();
        Map<InferencePolicyStrength, Integer> counts = new EnumMap<>(InferencePolicyStrength.class);
        for (InferencePolicyStrength strength : InferencePolicyStrength.values()) {
            counts.put(strength, 0);
        }

        for (PropertyPolicyDecision decision : decisions) {
            for (PropertyPolicyRule rule : decision.rules()) {
                InferencePolicy policy = rule.policy();
                InferencePolicyStrength strength = policy.strength();
                entries.add(new PropertyPolicyAuditEntry(
                        decision.propertyIri(),
                        rule.type(),
                        strength,
                        policy.enabled(),
                        decision.confidence(),
                        rule.targetPropertyIris(),
                        policy.rationale()
                ));
                counts.merge(strength, 1, Integer::sum);
            }
        }

        return new PropertyPolicyReport(List.copyOf(entries), Map.copyOf(counts));
    }
}
