package com.sahr.semantic.model;

import java.util.Objects;

public record RelationFamily(String id, String description) {
    public RelationFamily {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
