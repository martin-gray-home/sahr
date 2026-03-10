package com.sahr.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementParserNmodTest {
    private final StatementParser parser = new StatementParser();

    @Test
    void parsesWithPrepositionAsNmodPredicate() {
        Statement statement = parser.parse("The man is with a woman").orElseThrow();

        assertEquals("entity:man", statement.subject().value());
        assertEquals("entity:woman", statement.object().value());
        assertEquals("with", statement.predicate());
    }

    @Test
    void parsesCompoundSubjectAtLocation() {
        Statement statement = parser.parse("The man and the boy sat at the table").orElseThrow();

        java.util.List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);
        assertTrue(all.stream().anyMatch(item -> "entity:man_and_boy".equals(item.subject().value())));
        assertEquals("entity:table", statement.object().value());
        assertEquals("at", statement.predicate());
    }

    @Test
    void parsesMultipleNmodStatements() {
        Statement statement = parser.parse("The boy sat opposite the man at the table").orElseThrow();

        assertEquals("at", statement.predicate());
        assertEquals("entity:table", statement.object().value());
        assertEquals(1, statement.additionalStatements().size());
        assertEquals("opposite", statement.additionalStatements().get(0).predicate());
    }
}
