package com.sahr.nlp;

import org.junit.jupiter.api.Test;
import java.util.List;

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

    @Test
    void parsesMultiStatementSentence() {
        Statement statement = parser.parse("The man in the room is wearing a hat").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                "locatedIn".equals(item.predicate())
                        && "entity:man".equals(item.subject().value())
                        && "entity:room".equals(item.object().value())));
        assertTrue(all.stream().anyMatch(item ->
                "wear".equals(item.predicate())
                        && "entity:hat".equals(item.object().value())));
    }

    @Test
    void capturesAdjectiveAsAttributeStatement() {
        Statement statement = parser.parse("The red ball is on the table").orElseThrow();

        assertEquals("entity:ball", statement.subject().value());
        assertEquals("on", statement.predicate());
        assertEquals("entity:table", statement.object().value());
        assertTrue(statement.additionalStatements().stream().anyMatch(extra ->
                "hasAttribute".equals(extra.predicate())
                        && "entity:red".equals(extra.object().value())));
    }

    @Test
    void capturesRelativeClauseStatements() {
        Statement statement = parser.parse("The boy who is in the garden saw the dog").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                "locatedIn".equals(item.predicate())
                        && "entity:boy".equals(item.subject().value())
                        && "entity:garden".equals(item.object().value())));
    }

    @Test
    void capturesConjoinedVerbsAndAdverbs() {
        Statement statement = parser.parse("The man picked the box and carried the box quickly").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                ("pick".equals(item.predicate()) || "picked".equals(item.predicate()))
                        && "entity:man".equals(item.subject().value())
                        && "entity:box".equals(item.object().value())));
        assertTrue(all.stream().anyMatch(item ->
                "carry".equals(item.predicate())
                        && "entity:man".equals(item.subject().value())
                        && "entity:box".equals(item.object().value())));
        assertTrue(all.stream().anyMatch(item ->
                "hasManner".equals(item.predicate())
                        && "entity:quickly".equals(item.object().value())));
    }

    @Test
    void capturesPhrasalVerbWithParticle() {
        Statement statement = parser.parse("The man picked up the box").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                ("pick_up".equals(item.predicate()) || "pick".equals(item.predicate()) || "picked".equals(item.predicate()))
                        && "entity:man".equals(item.subject().value())
                        && "entity:box".equals(item.object().value())));
    }

    @Test
    void capturesAclNonRelativeClause() {
        Statement statement = parser.parse("The boy standing on the table waved").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                "on".equals(item.predicate())
                        && "entity:boy".equals(item.subject().value())
                        && "entity:table".equals(item.object().value())));
    }

    @Test
    void capturesClausalComplementStatements() {
        Statement statement = parser.parse("The man wants to carry the box").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                "carry".equals(item.predicate())
                        && "entity:man".equals(item.subject().value())
                        && "entity:box".equals(item.object().value())));
    }

    @Test
    void capturesPassiveSubjectAndAgent() {
        Statement statement = parser.parse("The box is carried by the man").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                ("carry".equals(item.predicate()) || "carried".equals(item.predicate()) || "carriedBy".equals(item.predicate()))
                        && "entity:box".equals(item.subject().value())
                        && "entity:man".equals(item.object().value())));
    }

    @Test
    void capturesCompoundNounObject() {
        Statement statement = parser.parse("The transmitter is powered by the power bus").orElseThrow();
        List<Statement> all = new java.util.ArrayList<>(statement.additionalStatements());
        all.add(statement);

        assertTrue(all.stream().anyMatch(item ->
                (item.predicate().contains("powered") || item.predicate().contains("power"))
                        && "entity:transmitter".equals(item.subject().value())
                        && "entity:power_bus".equals(item.object().value())));
    }
}