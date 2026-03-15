package com.sahr.ontology;

import com.sahr.nlp.TermMapper;

import java.util.Optional;

public final class SemanticNodeNormalizer {
    private final TermMapper termMapper;

    public SemanticNodeNormalizer(TermMapper termMapper) {
        this.termMapper = termMapper;
    }

    public Optional<String> canonicalType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return Optional.empty();
        }
        if (isIri(rawType)) {
            return Optional.of(rawType);
        }
        String stripped = stripPrefix(rawType);
        if (stripped.isBlank()) {
            return Optional.empty();
        }
        if (isIri(stripped)) {
            return Optional.of(stripped);
        }
        return termMapper.mapToken(stripped);
    }

    public String canonicalTypeOrConcept(String rawType) {
        Optional<String> canonical = canonicalType(rawType);
        if (canonical.isPresent()) {
            return canonical.get();
        }
        String stripped = stripPrefix(rawType == null ? "" : rawType);
        if (stripped.isBlank()) {
            return rawType;
        }
        return "concept:" + stripped;
    }

    private String stripPrefix(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("concept:")) {
            return value.substring("concept:".length());
        }
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        return value;
    }

    private boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }
}
