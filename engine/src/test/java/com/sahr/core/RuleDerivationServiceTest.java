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
        assertTrue(derivations.get(0).rule().contains("hasAttribute"));
        assertEquals("binding x=entity:hat", derivations.get(0).binding());
        assertTrue(derivations.get(0).evidence().stream().anyMatch(line -> line.contains("binding x=entity:hat")));
        assertTrue(derivations.get(0).supportingAssertionIds().stream().anyMatch(id -> !id.isBlank()));
    }

    @Test
    void derivesFromTwoVariableConjunctiveRuleBinding() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "ownedBy",
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "in",
                new SymbolId("entity:house"),
                0.95
        ));
        graph.addRuleFrame(new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "ownedBy", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house")),
                0.85
        ));

        List<RuleDerivation> derivations = service.derive(graph);

        assertEquals(1, derivations.size());
        assertEquals("entity:hat", derivations.get(0).assertion().subject().value());
        assertEquals("in", derivations.get(0).assertion().predicate());
        assertEquals("entity:house", derivations.get(0).assertion().object().value());
        assertEquals("binding x=entity:hat, y=entity:man", derivations.get(0).binding());
        assertTrue(derivations.get(0).rule().contains("forall x, y"));
        assertTrue(derivations.get(0).supportingAssertionIds().size() >= 2);
    }

    @Test
    void doesNotDeriveWhenConjunctiveAntecedentIsIncomplete() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "ownedBy",
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addRuleFrame(new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "ownedBy", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house")),
                0.85
        ));

        List<RuleDerivation> derivations = service.derive(graph);

        assertTrue(derivations.isEmpty());
    }

    @Test
    void derivesOneAssertionPerValidBindingEnvironment() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                "carry",
                new SymbolId("entity:bag"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                "in",
                new SymbolId("entity:garden"),
                0.95
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "carry",
                new SymbolId("entity:key"),
                0.88
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "in",
                new SymbolId("entity:room"),
                0.92
        ));
        graph.addRuleFrame(new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "carry", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));

        List<RuleDerivation> derivations = service.derive(graph);

        assertEquals(2, derivations.size());
        assertTrue(derivations.stream().anyMatch(derivation ->
                "entity:bag".equals(derivation.assertion().subject().value())
                        && "entity:garden".equals(derivation.assertion().object().value())
                        && "binding x=entity:woman, y=entity:bag, z=entity:garden".equals(derivation.binding())));
        assertTrue(derivations.stream().anyMatch(derivation ->
                "entity:key".equals(derivation.assertion().subject().value())
                        && "entity:room".equals(derivation.assertion().object().value())
                        && "binding x=entity:man, y=entity:key, z=entity:room".equals(derivation.binding())));
    }
}
