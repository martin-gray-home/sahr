package com.sahr.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementParserTest {
    private final StatementParser parser = new StatementParser();

    @Test
    void parsesLocatedInStatement() {
        Statement statement = parser.parse("The person is in the house").orElseThrow();

        assertEquals("entity:person", statement.subject().value());
        assertEquals("entity:house", statement.object().value());
        assertEquals("locatedIn", statement.predicate());
    }

    @Test
    void parsesTypeStatement() {
        Statement statement = parser.parse("A doctor is a person").orElseThrow();

        assertEquals("entity:doctor", statement.subject().value());
        assertEquals("concept:person", statement.object().value());
        assertEquals("rdf:type", statement.predicate());
        assertTrue(statement.objectIsConcept());
    }

    @Test
    void parsesVerbObjectStatement() {
        Statement statement = parser.parse("The man is wearing a hat").orElseThrow();

        assertEquals("entity:man", statement.subject().value());
        assertEquals("entity:hat", statement.object().value());
        assertEquals("wear", statement.predicate());
    }

    @Test
    void parsesWithHoldingAndCarryingStatements() {
        Statement with = parser.parse("The woman is with the man").orElseThrow();
        assertEquals("entity:woman", with.subject().value());
        assertEquals("entity:man", with.object().value());
        assertEquals("with", with.predicate());

        Statement carrying = parser.parse("The woman is carrying a bag").orElseThrow();
        assertEquals("entity:woman", carrying.subject().value());
        assertEquals("entity:bag", carrying.object().value());
        assertEquals("carry", carrying.predicate());

        Statement holding = parser.parse("The man is holding a key").orElseThrow();
        assertEquals("entity:man", holding.subject().value());
        assertEquals("entity:key", holding.object().value());
        assertEquals("hold", holding.predicate());
    }
}
