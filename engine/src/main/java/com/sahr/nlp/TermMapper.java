package com.sahr.nlp;

import java.util.Optional;

public interface TermMapper {
    Optional<String> mapToken(String token);

    Optional<String> mapPredicateToken(String token);
}
