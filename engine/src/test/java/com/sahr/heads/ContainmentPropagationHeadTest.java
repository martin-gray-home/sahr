package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentPropagationHeadTest {
    @Test
    void infersLocationFromContainment() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        ContainmentPropagationHead head = new ContainmentPropagationHead();

        String containment = "https://sahr.ai/ontology/relations#containment";
        String inside = "https://sahr.ai/ontology/relations#inside";
        ontology.addSubproperty(inside, containment);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:apple"),
                inside,
                new SymbolId("entity:basket"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:basket"),
                "locatedIn",
                new SymbolId("entity:kitchen"),
                0.9
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:apple"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:kitchen"));
        }));
    }
}