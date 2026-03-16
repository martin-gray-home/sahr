package com.sahr.semantic.model;

import java.util.Objects;

public record RoleType(String id, String description) {
    public RoleType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
