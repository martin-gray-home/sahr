package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationReasoningScenarioTest {
    @Test
    void answersLocationQueriesAfterPropagation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Assertion recorded.", agent.handle("A woman is with the man"));

        assertTrue(matchesLocation("entity:man", "entity:room", agent.handle("Where is the man")));
        assertEquals("entity:hat on entity:man", agent.handle("Where is the hat"));
        assertEquals("No candidates produced.", agent.handle("Where is the woman"));
    }

    private boolean matchesLocation(String subject, String object, String answer) {
        return (subject + " in " + object).equals(answer);
    }
}
