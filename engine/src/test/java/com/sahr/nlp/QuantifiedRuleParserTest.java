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
}
