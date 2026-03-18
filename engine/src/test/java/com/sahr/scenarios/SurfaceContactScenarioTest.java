package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceContactScenarioTest {
    @Test
    void answersSurfaceContactLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The hat is on the man"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));
    }
}
