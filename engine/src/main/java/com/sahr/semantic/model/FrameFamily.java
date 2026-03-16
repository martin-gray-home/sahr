package com.sahr.semantic.model;

import java.util.Objects;

public record FrameFamily(String id, String description) {
    public FrameFamily {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
