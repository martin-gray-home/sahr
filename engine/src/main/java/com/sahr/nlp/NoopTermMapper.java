package com.sahr.nlp;

import java.util.Optional;

public final class NoopTermMapper implements TermMapper {
    @Override
    public Optional<String> mapToken(String token) {
        return Optional.empty();
    }

    @Override
    public Optional<String> mapPredicateToken(String token) {
        return Optional.empty();
    }
}
