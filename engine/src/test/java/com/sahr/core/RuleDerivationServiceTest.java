package com.sahr.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDerivationServiceTest {
    @Test
    void derivesLegacyRuleAssertionsViaSharedRuleFrames() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        RelationAssertion antecedent = new RelationAssertion(
                new SymbolId("entity:motor"),
                "fail",
                new SymbolId("concept:true"),
                0.9
        );
        RelationAssertion consequent = new RelationAssertion(
                new SymbolId("entity:device"),
                "stop",
                new SymbolId("concept:true"),
                0.8
        );
        graph.addAssertion(antecedent);
        graph.addRule(new RuleAssertion(antecedent, consequent, 0.85));

        assertEquals(1, graph.getAllRuleFrames().size());
        assertEquals(1, graph.getAllRules().size());

        List<RuleDerivation> derivations = service.derive(graph);

        assertEquals(1, derivations.size());
        assertEquals("stop", derivations.get(0).assertion().predicate());
        assertEquals("entity:device", derivations.get(0).assertion().subject().value());
    }

    @Test
    void derivesRuleFramesFromSharedMatcher() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        graph.addEntity(new EntityNode(new SymbolId("entity:hat"), "hat", Set.of("hat")));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "in",
                new SymbolId("entity:house"),
                0.9
        ));
        graph.addRuleFrame(new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "rdf:type", RuleTerm.constant("concept:hat")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "hasAttribute", RuleTerm.constant("concept:green")),
                0.8
        ));

        List<RuleDerivation> derivations = service.derive(graph);

        assertEquals(1, derivations.size());
        assertEquals("hasAttribute", derivations.get(0).assertion().predicate());
        assertEquals("entity:hat", derivations.get(0).assertion().subject().value());
        assertEquals("concept:green", derivations.get(0).assertion().object().value());
        assertTrue(derivations.get(0).evidence().stream().anyMatch(line -> line.contains("binding x=entity:hat")));
        assertTrue(derivations.get(0).supportingAssertionIds().stream().anyMatch(id -> !id.isBlank()));
    }
}
