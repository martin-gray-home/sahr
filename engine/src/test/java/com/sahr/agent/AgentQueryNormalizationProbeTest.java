package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryFrame;
import com.sahr.core.QueryGoal;
import com.sahr.core.QueryNormalizer;
import com.sahr.core.QueryOperator;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQueryNormalizationProbeTest {
    @Test
    void normalizesRelationQueriesThroughAgentPath() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(new SymbolId("entity:hat"), "on", new SymbolId("entity:man"), 0.9));
        graph.addAssertion(new RelationAssertion(new SymbolId("entity:cat"), "under", new SymbolId("entity:hat"), 0.9));
        graph.addAssertion(new RelationAssertion(new SymbolId("entity:ball"), "in", new SymbolId("entity:house"), 0.9));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertNormalizedFrame(agent, "What is on the man", null, "on", "man", QueryFrame.TargetSlot.SUBJECT);
        assertNormalizedFrame(agent, "What is the hat on", "hat", "on", null, QueryFrame.TargetSlot.OBJECT);
        assertNormalizedFrame(agent, "What is under the hat", null, "under", "hat", QueryFrame.TargetSlot.SUBJECT);
        assertNormalizedFrame(agent, "What is in the house", null, "in", "house", QueryFrame.TargetSlot.SUBJECT);
    }

    private void assertNormalizedFrame(SahrAgent agent,
                                       String input,
                                       String expectedSubject,
                                       String expectedPredicate,
                                       String expectedObject,
                                       QueryFrame.TargetSlot expectedSlot) {
        agent.handle(input);
        QueryGoal query = agent.lastTraceEntry()
                .map(entry -> entry.query())
                .orElse(null);
        assertNotNull(query, "Expected trace entry for input: " + input);

        QueryNormalizer normalizer = new QueryNormalizer();
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, query.expectedType());

        recordFrameSnapshot(input, frame);

        if (!matchesEntityToken(expectedSubject, frame.subject())
                || !matchesPredicateToken(expectedPredicate, frame.predicate())
                || !matchesEntityToken(expectedObject, frame.object())
                || expectedSlot != frame.targetSlot()
                || QueryOperator.RETRIEVE != frame.operator()) {
            throw new AssertionError(String.format(
                    "Frame mismatch for '%s': subject=%s predicate=%s object=%s slot=%s operator=%s",
                    input, frame.subject(), frame.predicate(), frame.object(), frame.targetSlot(), frame.operator()
            ));
        }
    }

    private boolean matchesEntityToken(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        if (expected.equals(actual)) {
            return true;
        }
        return ("entity:" + expected).equals(actual);
    }

    private boolean matchesPredicateToken(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        int idx = Math.max(actual.lastIndexOf('#'), actual.lastIndexOf('/'));
        if (idx >= 0 && idx < actual.length() - 1) {
            String local = actual.substring(idx + 1);
            return expected.equals(local);
        }
        return false;
    }

    private void recordFrameSnapshot(String input, QueryFrame frame) {
        if (frame == null) {
            return;
        }
        String line = String.format("input=%s subject=%s predicate=%s object=%s slot=%s operator=%s%n",
                input, frame.subject(), frame.predicate(), frame.object(), frame.targetSlot(), frame.operator());
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/sahr_agent_query_probe.txt"),
                    line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
