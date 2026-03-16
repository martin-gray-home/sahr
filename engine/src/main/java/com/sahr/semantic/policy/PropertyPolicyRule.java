package com.sahr.semantic.policy;

import com.sahr.semantic.model.InferencePolicy;

import java.util.List;
import java.util.Objects;

public record PropertyPolicyRule(
        PropertyPolicyType type,
        InferencePolicy policy,
        List<String> targetPropertyIris
) {
    public PropertyPolicyRule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(targetPropertyIris, "targetPropertyIris");
    }
}
