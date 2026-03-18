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
}
