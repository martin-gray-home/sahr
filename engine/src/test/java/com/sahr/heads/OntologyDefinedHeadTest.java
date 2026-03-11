package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyDefinedHeadTest {
    @Test
    void infersTransitiveAssertionFromOntologyDefinition() throws Exception {
        OWLOntology ontology = loadTestOntology();
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        String locatedIn = "http://example.org/test#locatedIn";
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:a"),
                locatedIn,
                new SymbolId("entity:b"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:b"),
                locatedIn,
                new SymbolId("entity:c"),
                0.9
        ));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals(locatedIn)
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:a")
                && ((RelationAssertion) candidate.payload()).object().value().equals("entity:c")));
    }

    @Test
    void generatesMetaHeadFromTransitiveProperty() throws Exception {
        OWLOntology ontology = loadCoreTestOntology();
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        String locatedIn = "http://example.org/test#locatedIn";
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:a"),
                locatedIn,
                new SymbolId("entity:b"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:b"),
                locatedIn,
                new SymbolId("entity:c"),
                0.9
        ));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals(locatedIn)
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:a")
                        && ((RelationAssertion) candidate.payload()).object().value().equals("entity:c")));
    }

    private OWLOntology loadTestOntology() throws Exception {
        try (InputStream stream = OntologyDefinedHeadTest.class.getClassLoader()
                .getResourceAsStream("ontology/reasoning-heads-test.ttl")) {
            if (stream == null) {
                throw new IllegalStateException("Missing test ontology resource.");
            }
            var manager = OWLManager.createOWLOntologyManager();
            return manager.loadOntologyFromOntologyDocument(new StreamDocumentSource(stream));
        }
    }

    private OWLOntology loadCoreTestOntology() throws Exception {
        try (InputStream stream = OntologyDefinedHeadTest.class.getClassLoader()
                .getResourceAsStream("ontology/test-ontology.owl")) {
            if (stream == null) {
                throw new IllegalStateException("Missing core test ontology resource.");
            }
            var manager = OWLManager.createOWLOntologyManager();
            return manager.loadOntologyFromOntologyDocument(new StreamDocumentSource(stream));
        }
    }
}
