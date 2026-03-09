package com.sahr.nlp;

import java.util.Optional;

public final class NoopLexicalMapper implements LexicalMapper {
    @Override
    public Optional<String> mapToken(String token) {
        return Optional.empty();
    }
}
