package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantifiedRuleInsertionTest {
    @Test
    void recordsQuantifiedRuleFrameWithoutAssertions() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Rule recorded.", agent.handle("All hats in the house are green"));
        assertEquals(1, graph.getAllRuleFrames().size());
        assertTrue(graph.getAllAssertions().isEmpty(), "Expected no assertions created from rule insertion.");
    }
}
