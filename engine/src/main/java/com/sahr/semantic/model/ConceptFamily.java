package com.sahr.semantic.model;

import java.util.Objects;

public record ConceptFamily(String id, String description) {
    public ConceptFamily {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
