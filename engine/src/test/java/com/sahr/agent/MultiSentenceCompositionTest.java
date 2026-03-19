package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.AssertionLayer;
import com.sahr.core.AssertionRecord;
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
        AssertionRecord derived = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.INFERRED)
                .filter(record -> "entity:hat".equals(record.subject().value()))
                .filter(record -> record.predicate().toLowerCase().contains("hasattribute"))
                .findFirst()
                .orElse(null);
        assertTrue(derived != null && !derived.provenance().supportingAssertionIds().isEmpty());
        assertTrue(derived != null && !derived.provenance().derivationRule().isBlank());
        assertTrue(derived != null && !derived.provenance().derivationBinding().isBlank());
        assertTrue(agent.lastTraceEntry().isPresent());
        assertNotEquals(QueryGoal.Type.UNKNOWN, agent.lastTraceEntry().orElseThrow().query().type());
    }

    @Test
    void explainShowsSegmentOriginsForComposedReasoning() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        String answer = agent.handle("All hats in the house are green. The hat is in the house. What is green?");

        assertEquals("entity:hat", answer);
        String explain = new CommandProcessor(agent).handle(":explain --depth 3 --verbose").output();
        assertTrue(explain.contains("[segment:s3/3"), explain);
        assertTrue(explain.contains("[segment:s2/3"), explain);
        assertTrue(explain.contains("Derivations"), explain);
        assertTrue(explain.contains("rule="), explain);
        assertTrue(explain.contains("binding x=entity:hat"), explain);
        assertTrue(explain.contains("[supports:"), explain);
        assertTrue(explain.contains("supports=assertion-"), explain);
        assertTrue(explain.contains("entity:hat in entity:house"), explain);
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
