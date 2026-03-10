package com.sahr.nlp;

import java.util.List;
import java.util.Optional;

public final class CompositeTermMapper implements TermMapper {
    private final List<TermMapper> delegates;

    public CompositeTermMapper(List<TermMapper> delegates) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
    }

    @Override
    public Optional<String> mapToken(String token) {
        for (TermMapper delegate : delegates) {
            Optional<String> mapped = delegate.mapToken(token);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> mapPredicateToken(String token) {
        for (TermMapper delegate : delegates) {
            Optional<String> mapped = delegate.mapPredicateToken(token);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }
}
