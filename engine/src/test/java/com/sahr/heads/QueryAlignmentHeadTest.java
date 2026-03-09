package com.sahr.heads;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAlignmentHeadTest {
    private final QueryAlignmentHead head = new QueryAlignmentHead();

    @Test
    void alignsPredicateUsingRangeMatch() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId catId = new SymbolId("entity:cat");
        graph.addEntity(new EntityNode(catId, "cat", Set.of("cat")));
        graph.addAssertion(new RelationAssertion(
                catId,
                "http://example.org/test#inside",
                new SymbolId("entity:box"),
                0.8
        ));

        InMemoryOntologyService ontology = new InMemoryOntologyService();
        ontology.addPropertyRange("http://example.org/test#inside", "http://example.org/test#Place");

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("cat", "http://example.org/test#Place"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate ->
                "entity:cat http://example.org/test#inside entity:box".equals(candidate.payload())));
    }
}
