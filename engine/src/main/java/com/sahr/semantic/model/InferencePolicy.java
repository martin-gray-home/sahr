package com.sahr.semantic.model;

import java.util.Objects;

public record InferencePolicy(InferencePolicyStrength strength, boolean enabled, String rationale) {
    public InferencePolicy {
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(rationale, "rationale");
    }
}
