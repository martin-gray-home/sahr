package com.sahr.semantic.policy;

import com.sahr.semantic.model.InferencePolicy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PropertyPolicyRegistry {
    private final Map<String, PropertyPolicyDecision> decisionsByProperty;

    private PropertyPolicyRegistry(Map<String, PropertyPolicyDecision> decisionsByProperty) {
        this.decisionsByProperty = Map.copyOf(decisionsByProperty);
    }

    public static PropertyPolicyRegistry fromDecisions(List<PropertyPolicyDecision> decisions) {
        Objects.requireNonNull(decisions, "decisions");
        Map<String, PropertyPolicyDecision> map = new HashMap<>();
        for (PropertyPolicyDecision decision : decisions) {
            map.put(decision.propertyIri(), decision);
        }
        return new PropertyPolicyRegistry(map);
    }

    public Optional<PropertyPolicyDecision> decision(String propertyIri) {
        if (propertyIri == null || propertyIri.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(decisionsByProperty.get(propertyIri));
    }

    public Optional<InferencePolicy> inversePolicy(String propertyIri) {
        return policyFor(propertyIri, PropertyPolicyType.INVERSE);
    }

    public Optional<InferencePolicy> symmetricPolicy(String propertyIri) {
        return policyFor(propertyIri, PropertyPolicyType.SYMMETRIC);
    }

    public Optional<InferencePolicy> transitivePolicy(String propertyIri) {
        return policyFor(propertyIri, PropertyPolicyType.TRANSITIVE);
    }

    public Optional<String> inverseProperty(String propertyIri) {
        Optional<PropertyPolicyRule> rule = ruleFor(propertyIri, PropertyPolicyType.INVERSE);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        if (!isEnabled(rule.get().policy())) {
            return Optional.empty();
        }
        return rule.get().targetPropertyIris().stream().findFirst();
    }

    private Optional<InferencePolicy> policyFor(String propertyIri, PropertyPolicyType type) {
        Optional<PropertyPolicyRule> rule = ruleFor(propertyIri, type);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        InferencePolicy policy = rule.get().policy();
        if (!isEnabled(policy)) {
            return Optional.empty();
        }
        return Optional.of(policy);
    }

    private Optional<PropertyPolicyRule> ruleFor(String propertyIri, PropertyPolicyType type) {
        return decision(propertyIri)
                .flatMap(decision -> decision.rules().stream()
                        .filter(rule -> rule.type() == type)
                        .findFirst());
    }

    private boolean isEnabled(InferencePolicy policy) {
        return policy.enabled() && policy.strength() != com.sahr.semantic.model.InferencePolicyStrength.DISABLED;
    }
}
