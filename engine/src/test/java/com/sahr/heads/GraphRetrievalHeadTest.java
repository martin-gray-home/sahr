package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphRetrievalHeadTest {
    @Test
    void resolvesNestedLocationChain() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        GraphRetrievalHead head = new GraphRetrievalHead();

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:apple"),
                "inside",
                new SymbolId("entity:basket"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:basket"),
                "locatedIn",
                new SymbolId("entity:kitchen"),
                0.9
        ));
        graph.addEntity(new com.sahr.core.EntityNode(
                new SymbolId("entity:apple"),
                "apple",
                java.util.Set.of("concept:apple")
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("concept:apple", "concept:location"),
                graph,
                ontology,
                new WorkingMemory()
        ));

        assertTrue(candidates.stream().anyMatch(candidate ->
                "entity:apple locatedIn entity:kitchen".equals(candidate.payload())));
    }
}
