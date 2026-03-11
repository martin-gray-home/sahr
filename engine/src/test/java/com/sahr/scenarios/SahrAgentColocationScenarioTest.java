package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentColocationScenarioTest {
    @Test
    void answersLocationViaWithRelation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The woman is with the man"));

        assertTrue(graph.getAllAssertions().stream().anyMatch(assertion -> {
                String predicate = assertion.predicate();
                return ("in".equals(predicate) || predicate.endsWith("#in"))
                        && "entity:man".equals(assertion.subject().value())
                        && "entity:room".equals(assertion.object().value());
        }));
        assertTrue(graph.getAllAssertions().stream().anyMatch(assertion -> {
                String predicate = assertion.predicate();
                return ("with".equals(predicate) || predicate.endsWith("#with"))
                        && "entity:woman".equals(assertion.subject().value())
                        && "entity:man".equals(assertion.object().value());
        }));

        String answer = agent.handle("Where is the woman");
        assertEquals("entity:woman in entity:room", answer);
    }
}
