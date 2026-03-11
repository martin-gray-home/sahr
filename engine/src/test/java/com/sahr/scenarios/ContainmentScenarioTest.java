package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainmentScenarioTest {
    @Test
    void answersContainmentLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The apple is inside the basket"));
        assertEquals("Assertion recorded.", agent.handle("The basket is in the kitchen"));
        assertEquals("entity:apple in entity:kitchen", agent.handle("Where is the apple"));
    }
}
