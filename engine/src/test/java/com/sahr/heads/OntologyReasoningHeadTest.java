package com.sahr.heads;

import com.sahr.core.CandidateType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyReasoningHeadTest {
    private final OntologyReasoningHead head = new OntologyReasoningHead();

    @Test
    void infersSymmetricAssertions() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        ontology.addSymmetricProperty("relatedTo");

        RelationAssertion assertion = new RelationAssertion(
                new SymbolId("entity:a"),
                "relatedTo",
                new SymbolId("entity:b"),
                0.8
        );
        graph.addAssertion(assertion);

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            if (candidate.type() != CandidateType.ASSERTION) {
                return false;
            }
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:b"))
                    && inferred.predicate().equals("relatedTo")
                    && inferred.object().equals(new SymbolId("entity:a"));
        }));
    }

    @Test
    void infersInverseAssertions() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        ontology.addInverseProperty("at", "contains");

        RelationAssertion assertion = new RelationAssertion(
                new SymbolId("entity:wife"),
                "at",
                new SymbolId("entity:table"),
                0.9
        );
        graph.addAssertion(assertion);

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            if (candidate.type() != CandidateType.ASSERTION) {
                return false;
            }
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:table"))
                    && inferred.predicate().equals("contains")
                    && inferred.object().equals(new SymbolId("entity:wife"));
        }));
    }

    @Test
    void infersTransitiveAssertions() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        ontology.addTransitiveProperty("locatedIn");

        RelationAssertion first = new RelationAssertion(
                new SymbolId("entity:wife"),
                "locatedIn",
                new SymbolId("entity:room"),
                0.7
        );
        RelationAssertion second = new RelationAssertion(
                new SymbolId("entity:room"),
                "locatedIn",
                new SymbolId("entity:house"),
                0.6
        );

        graph.addAssertion(first);
        graph.addAssertion(second);

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                ontology
        ));

        assertEquals(1, candidates.stream().filter(candidate -> {
            if (candidate.type() != CandidateType.ASSERTION) {
                return false;
            }
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:wife"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:house"));
        }).count());
    }
}