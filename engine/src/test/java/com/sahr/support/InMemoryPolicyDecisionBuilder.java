package com.sahr.support;

import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import com.sahr.semantic.policy.PropertyPolicyDecision;
import com.sahr.semantic.policy.PropertyPolicyRule;
import com.sahr.semantic.policy.PropertyPolicyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryPolicyDecisionBuilder {
    private InMemoryPolicyDecisionBuilder() {
    }

    public static List<PropertyPolicyDecision> build(InMemoryOntologyService ontology) {
        Map<String, List<PropertyPolicyRule>> rulesByProperty = new HashMap<>();
        InferencePolicy policy = new InferencePolicy(InferencePolicyStrength.HARD, true, "test in-memory policy");

        for (String property : ontology.getSymmetricProperties()) {
            addRule(rulesByProperty, property, new PropertyPolicyRule(
                    PropertyPolicyType.SYMMETRIC,
                    policy,
                    List.of(property)
            ));
        }

        for (String property : ontology.getTransitiveProperties()) {
            addRule(rulesByProperty, property, new PropertyPolicyRule(
                    PropertyPolicyType.TRANSITIVE,
                    policy,
                    List.of(property)
            ));
        }

        for (Map.Entry<String, String> entry : ontology.getInverseProperties().entrySet()) {
            String property = entry.getKey();
            String inverse = entry.getValue();
            if (property == null || inverse == null) {
                continue;
            }
            addRule(rulesByProperty, property, new PropertyPolicyRule(
                    PropertyPolicyType.INVERSE,
                    policy,
                    List.of(inverse)
            ));
        }

        List<PropertyPolicyDecision> decisions = new ArrayList<>();
        for (Map.Entry<String, List<PropertyPolicyRule>> entry : rulesByProperty.entrySet()) {
            decisions.add(new PropertyPolicyDecision(
                    entry.getKey(),
                    AlignmentConfidence.MANUAL_OVERRIDE,
                    List.copyOf(entry.getValue())
            ));
        }
        return decisions;
    }

    private static void addRule(Map<String, List<PropertyPolicyRule>> rulesByProperty,
                                String property,
                                PropertyPolicyRule rule) {
        if (property == null || property.isBlank()) {
            return;
        }
        rulesByProperty.computeIfAbsent(property, key -> new ArrayList<>()).add(rule);
    }
}
