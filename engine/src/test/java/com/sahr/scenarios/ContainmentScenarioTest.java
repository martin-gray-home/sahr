package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentScenarioTest {
    @Test
    void answersContainmentLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The apple is inside the basket"));
        assertEquals("Assertion recorded.", agent.handle("The basket is in the kitchen"));
        assertEquals("entity:apple in entity:basket", agent.handle("Where is the apple"));
    }

    @Test
    void answersContainsQueryUsingOntologyOnlyPredicate() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The box contains the ball"));
        assertEquals("entity:ball", agent.handle("What does the box contain"));
        assertEquals("entity:ball", agent.handle("What is in the box"));
        assertEquals("1", agent.handle("How many things are in the box"));

        String existsAnswer = agent.handle("Is anything in the box");
        assertTrue(existsAnswer.startsWith("Yes,"),
                "Expected yes/no confirmation for containment query, got: " + existsAnswer);
    }
}
