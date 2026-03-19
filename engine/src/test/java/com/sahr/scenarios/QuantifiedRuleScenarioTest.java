package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantifiedRuleScenarioTest {
    @Test
    void infersAttributeFromQuantifiedRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Rule recorded.", agent.handle("All hats in the house are green"));
        assertEquals(1, graph.getAllRuleFrames().size());
        assertEquals("Assertion recorded.", agent.handle("The hat is in the house"));
        assertTrue(graph.findEntity(new com.sahr.core.SymbolId("entity:hat")).isPresent(),
                "Expected entity:hat to be present after assertion ingestion.");
        String hatTypes = graph.findEntity(new com.sahr.core.SymbolId("entity:hat"))
                .map(entity -> entity.conceptTypes().toString())
                .orElse("<missing>");
        String rulePredicate = graph.getAllRuleFrames().get(0).antecedents().get(1).predicate();
        String availablePredicates = graph.getAllAssertions().stream()
                .map(assertion -> assertion.predicate())
                .distinct()
                .toList()
                .toString();
        assertTrue(graph.findByPredicate(rulePredicate).stream()
                        .anyMatch(assertion -> "entity:hat".equals(assertion.subject().value())),
                "Expected antecedent predicate " + rulePredicate + " to match assertions. Available predicates: "
                        + availablePredicates);
        String consequentPredicate = graph.getAllRuleFrames().get(0).consequent().predicate();
        assertTrue(graph.findByPredicate(consequentPredicate).stream()
                        .anyMatch(assertion -> "entity:hat".equals(assertion.subject().value())
                                && "concept:green".equals(assertion.object().value())),
                "Expected derived hasAttribute assertion after rule forward chaining. Available predicates: "
                        + availablePredicates + " hatTypes=" + hatTypes);
        assertEquals("entity:hat", agent.handle("What is green"));

        String yesNo = agent.handle("Is the hat green");
        assertTrue(yesNo.startsWith("Yes"), "Expected yes/no confirmation, got: " + yesNo);
    }

    @Test
    void infersAttributeFromConditionalRuleFrame() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Rule recorded.", agent.handle("If a hat is in the house, then it is green"));
        assertEquals(1, graph.getAllRuleFrames().size());
        assertEquals("Assertion recorded.", agent.handle("The hat is in the house"));

        String consequentPredicate = graph.getAllRuleFrames().get(0).consequent().predicate();
        assertTrue(graph.findByPredicate(consequentPredicate).stream()
                        .anyMatch(assertion -> "entity:hat".equals(assertion.subject().value())
                                && "concept:green".equals(assertion.object().value())),
                "Expected derived hasAttribute assertion after conditional rule forward chaining.");
        assertEquals("entity:hat", agent.handle("What is green"));

        String yesNo = agent.handle("Is the hat green");
        assertTrue(yesNo.startsWith("Yes"), "Expected yes/no confirmation, got: " + yesNo);
    }

    @Test
    void infersLocationFromLanguageIngestedGenericCarryRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Rule recorded.",
                agent.handle("If someone carries something and is in a place, then that thing is in that place"));
        assertEquals("Assertion recorded.", agent.handle("The woman is in the garden"));
        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));

        assertEquals("entity:garden", agent.handle("What is the bag in?"));
    }

    @Test
    void infersLocationFromLanguageIngestedGenericPartOfRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Rule recorded.",
                agent.handle("If something is part of something and that thing is in a place, then the first thing is in that place"));
        assertEquals("Assertion recorded.", agent.handle("The handle is part of the door"));
        assertEquals("Assertion recorded.", agent.handle("The door is in the house"));

        assertEquals("entity:house", agent.handle("What is the handle in?"));
    }
}
