package com.sahr.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatementParserNmodTest {
    private final StatementParser parser = new StatementParser();

    @Test
    void parsesWithPrepositionAsNmodPredicate() {
        Statement statement = parser.parse("The man is with a woman").orElseThrow();

        assertEquals("entity:man", statement.subject().value());
        assertEquals("entity:woman", statement.object().value());
        assertEquals("with", statement.predicate());
    }
}
