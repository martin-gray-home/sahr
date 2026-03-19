package com.sahr.nlp;

import com.sahr.core.RuleAtom;
import com.sahr.core.RuleFrame;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantifiedRuleParserTest {
    @Test
    void parsesAllInAreRule() {
        QuantifiedRuleParser parser = new QuantifiedRuleParser();
        Optional<RuleFrame> rule = parser.parse("All hats in the house are green.");

        assertTrue(rule.isPresent());
        assertEquals("x", rule.get().variable());
        assertEquals(2, rule.get().antecedents().size());
        RuleAtom typeAtom = rule.get().antecedents().get(0);
        RuleAtom inAtom = rule.get().antecedents().get(1);
        assertEquals("rdf:type", typeAtom.predicate());
        assertEquals("concept:hat", typeAtom.object().value());
        assertEquals("in", inAtom.predicate());
        assertEquals("entity:house", inAtom.object().value());
        assertEquals("hasAttribute", rule.get().consequent().predicate());
        assertEquals("concept:green", rule.get().consequent().object().value());
    }

    @Test
    void parsesConditionalInAttributeRule() {
        QuantifiedRuleParser parser = new QuantifiedRuleParser();
        Optional<RuleFrame> rule = parser.parse("If a hat is in the house, then it is green.");

        assertTrue(rule.isPresent());
        assertEquals("x", rule.get().variable());
        assertEquals(2, rule.get().antecedents().size());
        RuleAtom typeAtom = rule.get().antecedents().get(0);
        RuleAtom inAtom = rule.get().antecedents().get(1);
        assertEquals("rdf:type", typeAtom.predicate());
        assertEquals("concept:hat", typeAtom.object().value());
        assertEquals("in", inAtom.predicate());
        assertEquals("entity:house", inAtom.object().value());
        assertEquals("hasAttribute", rule.get().consequent().predicate());
        assertEquals("concept:green", rule.get().consequent().object().value());
    }

    @Test
    void parsesGenericCarryLocationTransferRule() {
        QuantifiedRuleParser parser = new QuantifiedRuleParser();
        Optional<RuleFrame> rule = parser.parse(
                "If someone carries something and is in a place, then that thing is in that place.");

        assertTrue(rule.isPresent());
        assertEquals("x", rule.get().variable());
        assertEquals(java.util.List.of("x", "y", "z"), rule.get().variables());
        assertEquals(2, rule.get().antecedents().size());
        RuleAtom carryAtom = rule.get().antecedents().get(0);
        RuleAtom inAtom = rule.get().antecedents().get(1);
        assertEquals("carry", carryAtom.predicate());
        assertTrue(carryAtom.subject().isVariable());
        assertEquals("x", carryAtom.subject().value());
        assertTrue(carryAtom.object().isVariable());
        assertEquals("y", carryAtom.object().value());
        assertEquals("in", inAtom.predicate());
        assertTrue(inAtom.subject().isVariable());
        assertEquals("x", inAtom.subject().value());
        assertTrue(inAtom.object().isVariable());
        assertEquals("z", inAtom.object().value());
        assertEquals("in", rule.get().consequent().predicate());
        assertTrue(rule.get().consequent().subject().isVariable());
        assertEquals("y", rule.get().consequent().subject().value());
        assertTrue(rule.get().consequent().object().isVariable());
        assertEquals("z", rule.get().consequent().object().value());
    }

    @Test
    void parsesGenericPartOfLocationTransferRule() {
        QuantifiedRuleParser parser = new QuantifiedRuleParser();
        Optional<RuleFrame> rule = parser.parse(
                "If something is part of something and that thing is in a place, then the first thing is in that place.");

        assertTrue(rule.isPresent());
        assertEquals("x", rule.get().variable());
        assertEquals(java.util.List.of("x", "y", "z"), rule.get().variables());
        assertEquals(2, rule.get().antecedents().size());
        RuleAtom partOfAtom = rule.get().antecedents().get(0);
        RuleAtom inAtom = rule.get().antecedents().get(1);
        assertEquals("partOf", partOfAtom.predicate());
        assertTrue(partOfAtom.subject().isVariable());
        assertEquals("x", partOfAtom.subject().value());
        assertTrue(partOfAtom.object().isVariable());
        assertEquals("y", partOfAtom.object().value());
        assertEquals("in", inAtom.predicate());
        assertTrue(inAtom.subject().isVariable());
        assertEquals("y", inAtom.subject().value());
        assertTrue(inAtom.object().isVariable());
        assertEquals("z", inAtom.object().value());
        assertEquals("in", rule.get().consequent().predicate());
        assertTrue(rule.get().consequent().subject().isVariable());
        assertEquals("x", rule.get().consequent().subject().value());
        assertTrue(rule.get().consequent().object().isVariable());
        assertEquals("z", rule.get().consequent().object().value());
    }
}
