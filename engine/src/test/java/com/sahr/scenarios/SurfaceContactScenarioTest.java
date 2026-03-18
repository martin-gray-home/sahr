package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceContactScenarioTest {
    @Test
    void answersSurfaceContactLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The hat is on the man"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        String actual = agent.handle("Where is the hat");
        assertTrue(Set.of("entity:hat in entity:room", "entity:hat inside entity:room", "entity:hat on entity:man")
                        .contains(actual),
                () -> "Unexpected answer: " + actual);
    }
}
