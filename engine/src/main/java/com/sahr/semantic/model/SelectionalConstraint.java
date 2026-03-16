package com.sahr.semantic.model;

import java.util.Objects;

public record SelectionalConstraint(
        RoleType role,
        ConceptFamily requiredFamily,
        ConstraintStrength strength,
        String rationale
) {
    public SelectionalConstraint {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(requiredFamily, "requiredFamily");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(rationale, "rationale");
    }
}
