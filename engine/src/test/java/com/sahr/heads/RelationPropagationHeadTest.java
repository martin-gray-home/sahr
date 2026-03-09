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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationPropagationHeadTest {
    private final RelationPropagationHead head = new RelationPropagationHead();

    @Test
    void infersLocatedInFromAtAndLocatedIn() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "at",
                new SymbolId("entity:room"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:room"),
                "locatedIn",
                new SymbolId("entity:house"),
                0.8
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                new InMemoryOntologyService()
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:man"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:house"));
        }));
    }

    @Test
    void infersTransitiveLocatedIn() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:room"),
                "locatedIn",
                new SymbolId("entity:house"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:house"),
                "locatedIn",
                new SymbolId("entity:city"),
                0.9
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                new InMemoryOntologyService()
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:room"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:city"));
        }));
    }

    @Test
    void infersCoLocationFromWear() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "wear",
                new SymbolId("entity:hat"),
                0.8
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
                new InMemoryOntologyService()
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:hat"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:room"));
        }));
    }

    @Test
    void infersCoLocationFromWith() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "with",
                new SymbolId("entity:woman"),
                0.8
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "locatedIn",
                new SymbolId("entity:park"),
                0.9
        ));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("entity", "location"),
                graph,
                new InMemoryOntologyService()
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:woman"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:park"));
        }));
    }

    @Test
    void infersCoLocationWhenLocatedEntityIsObject() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                "with",
                new SymbolId("entity:man"),
                0.8
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
                new InMemoryOntologyService()
        ));

        assertTrue(candidates.stream().anyMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:woman"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:room"));
        }));
    }

    @Test
    void infersCoLocationFromOntologySubproperty() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        RelationPropagationHead head = new RelationPropagationHead(Set.of("https://sahr.ai/ontology/relations#colocation"));

        String colocation = "https://sahr.ai/ontology/relations#colocation";
        String wear = "https://sahr.ai/ontology/relations#wear";
        ontology.addSubproperty(wear, colocation);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                wear,
                new SymbolId("entity:hat"),
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

    @Test
    void infersCoLocationFromInverseProperty() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        RelationPropagationHead head = new RelationPropagationHead(Set.of("https://sahr.ai/ontology/relations#colocation"));

        String colocation = "https://sahr.ai/ontology/relations#colocation";
        String wear = "https://sahr.ai/ontology/relations#wear";
        String wornBy = "https://sahr.ai/ontology/relations#wornBy";
        ontology.addSubproperty(wear, colocation);
        ontology.addInverseProperty(wear, wornBy);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                wornBy,
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

    @Test
    void doesNotPropagateForNonColocationFamily() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        RelationPropagationHead head = new RelationPropagationHead(Set.of("https://sahr.ai/ontology/relations#colocation"));

        String surfaceContact = "https://sahr.ai/ontology/relations#surfaceContact";
        String on = "https://sahr.ai/ontology/relations#on";
        ontology.addSubproperty(on, surfaceContact);

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                on,
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

        assertTrue(candidates.stream().noneMatch(candidate -> {
            RelationAssertion inferred = (RelationAssertion) candidate.payload();
            return inferred.subject().equals(new SymbolId("entity:hat"))
                    && inferred.predicate().equals("locatedIn")
                    && inferred.object().equals(new SymbolId("entity:room"));
        }));
    }
}
