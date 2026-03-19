package com.sahr.scenarios;

import com.sahr.agent.CommandProcessor;
import com.sahr.agent.SahrAgent;
import com.sahr.core.AssertionLayer;
import com.sahr.core.AssertionRecord;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceContactContainmentScenarioTest {
    @Test
    void answersContainmentQueryThroughSurfaceContactComposition() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The cat sat on the mat"));
        assertEquals("Assertion recorded.", agent.handle("The mat is in the house"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the house"));

        String answer = agent.handle("What is in the house");

        assertEquals(Set.of("entity:cat", "entity:mat", "entity:man"), parseAnswerSet(answer));
        AssertionRecord derived = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.INFERRED)
                .filter(record -> "entity:cat".equals(record.subject().value()))
                .filter(record -> "in".equals(record.predicate())
                        || "https://sahr.ai/ontology/relations#in".equals(record.predicate()))
                .filter(record -> "entity:house".equals(record.object().value()))
                .findFirst()
                .orElseThrow();
        assertEquals(AssertionLayer.INFERRED, derived.layer());
    }

    @Test
    void explainShowsSurfaceContactContainmentDerivationPath() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The cat sat on the mat"));
        assertEquals("Assertion recorded.", agent.handle("The mat is in the house"));
        assertEquals("entity:house", agent.handle("What is the cat in?"));

        String explain = new CommandProcessor(agent).handle(":explain --depth 3 --verbose").output();

        assertTrue(explain.contains("entity:cat") && explain.contains("entity:mat"), explain);
        assertTrue(explain.contains("https://sahr.ai/ontology/relations#on"), explain);
        assertTrue(explain.contains("entity:mat") && explain.contains("entity:house"), explain);
        assertTrue(explain.contains("https://sahr.ai/ontology/relations#in"), explain);
        assertTrue(explain.contains("entity:cat") && explain.contains("entity:house"), explain);
    }

    private Set<String> parseAnswerSet(String answer) {
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
