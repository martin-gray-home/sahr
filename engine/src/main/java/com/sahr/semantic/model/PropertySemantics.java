package com.sahr.semantic.model;

import java.util.List;
import java.util.Objects;

public record PropertySemantics(
        String propertyIri,
        List<String> domainIris,
        List<String> rangeIris,
        List<String> inversePropertyIris,
        boolean symmetric,
        boolean transitive,
        List<SemanticSourceReference> sources
) {
    public PropertySemantics {
        Objects.requireNonNull(propertyIri, "propertyIri");
        Objects.requireNonNull(domainIris, "domainIris");
        Objects.requireNonNull(rangeIris, "rangeIris");
        Objects.requireNonNull(inversePropertyIris, "inversePropertyIris");
        Objects.requireNonNull(sources, "sources");
    }
}
