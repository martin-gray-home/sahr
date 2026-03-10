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

class SurfaceContactPropagationHeadTest {
    @Test
    void infersLocationFromSurfaceContact() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        SurfaceContactPropagationHead head = new SurfaceContactPropagationHead();

        String surface = "https://sahr.ai/ontology/relations#surfaceContact";
        String on = "https://sahr.ai/ontology/relations#on";
        ontology.addSubproperty(on, surface);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:cup"),
                on,
                new SymbolId("entity:table"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:table"),
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
            return inferred.subject().equals(new SymbolId("entity:cup"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:kitchen"));
        }));
    }

    @Test
    void infersLocationFromInverseSurfaceContact() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        SurfaceContactPropagationHead head = new SurfaceContactPropagationHead();

        String surface = "https://sahr.ai/ontology/relations#surfaceContact";
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        ontology.addSubproperty(on, surface);
        ontology.addInverseProperty(on, under);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                under,
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "locatedIn",
                new SymbolId("entity:room"),
                0.9
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:hat"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:room"));
        }));
    }
}