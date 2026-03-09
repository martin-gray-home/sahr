package com.sahr.nlp;

import java.util.Optional;

public interface LexicalMapper {
    Optional<String> mapToken(String token);
}
