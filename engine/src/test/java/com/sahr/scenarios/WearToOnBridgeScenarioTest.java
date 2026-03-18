package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WearToOnBridgeScenarioTest {
    @Test
    void answersOnQueriesFromWearAssertions() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:hat", agent.handle("What is on the man"));
        assertEquals("entity:man", agent.handle("What is the hat on"));
    }
}
