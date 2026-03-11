package com.sahr.agent;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentSubgoalTest {
    @Test
    void resolvesWhereQueryViaSubgoalQueue() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();

        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");
        graph.addEntity(new EntityNode(man, "man", Set.of("concept:man")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("concept:hat")));
        graph.addAssertion(new RelationAssertion(man, "https://sahr.ai/ontology/relations#wear", hat, 0.9));
        graph.addAssertion(new RelationAssertion(man, "https://sahr.ai/ontology/relations#in", new SymbolId("entity:room"), 0.9));

        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));

        boolean sawSubgoal = agent.trace()
                .map(trace -> trace.entries().stream()
                        .flatMap(entry -> entry.candidates().stream())
                        .anyMatch(candidate -> candidate.type() == CandidateType.SUBGOAL))
                .orElse(false);
        assertTrue(sawSubgoal);
    }
}
