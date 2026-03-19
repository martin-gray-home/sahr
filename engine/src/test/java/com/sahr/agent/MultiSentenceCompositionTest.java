package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSentenceCompositionTest {
    @Test
    void composesRuleFactAndQuestionAcrossSentenceSegments() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        String answer = agent.handle("All hats in the house are green. The hat is in the house. What is green?");

        assertEquals("entity:hat", answer);
        assertTrue(graph.getAllAssertions().stream()
                .anyMatch(assertion -> "entity:hat".equals(assertion.subject().value())
                        && assertion.predicate().toLowerCase().contains("hasattribute")
                        && "concept:green".equals(assertion.object().value())));
        assertTrue(agent.lastTraceEntry().isPresent());
        assertNotEquals(QueryGoal.Type.UNKNOWN, agent.lastTraceEntry().orElseThrow().query().type());
    }

    @Test
    void composesSequentialAssertionsBeforeFinalQuestion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        String answer = agent.handle("The man is in the room. The man is wearing a hat. Where is the hat?");

        assertEquals("entity:hat on entity:man", answer);
        assertTrue(graph.findEntity(new SymbolId("entity:man")).isPresent());
        assertTrue(graph.findEntity(new SymbolId("entity:hat")).isPresent());
    }
}
