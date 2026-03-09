package com.sahr.core;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class EntityNode {
    private final SymbolId id;
    private final String surfaceForm;
    private final Set<String> conceptTypes;

    public EntityNode(SymbolId id, String surfaceForm, Set<String> conceptTypes) {
        this.id = Objects.requireNonNull(id, "id");
        this.surfaceForm = Objects.requireNonNull(surfaceForm, "surfaceForm");
        this.conceptTypes = Collections.unmodifiableSet(Objects.requireNonNull(conceptTypes, "conceptTypes"));
    }

    public SymbolId id() {
        return id;
    }

    public String surfaceForm() {
        return surfaceForm;
    }

    public Set<String> conceptTypes() {
        return conceptTypes;
    }
}
