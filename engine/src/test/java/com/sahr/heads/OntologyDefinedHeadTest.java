package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;
import com.sahr.core.RuleAssertion;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.support.OwlOntologyTestSupport;
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
        List<OntologyHeadDefinition> definitions = OwlOntologyTestSupport.buildHeadDefinitions();
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        String locatedIn = "https://sahr.ai/ontology/relations#locatedIn";
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

    @Test
    void appliesRuleForwardChainingFromGraphRules() throws Exception {
        OWLOntology ontology = loadTestOntology();
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RelationAssertion antecedent = new RelationAssertion(
                new SymbolId("entity:motor"),
                "fail",
                new SymbolId("concept:true"),
                0.9
        );
        RelationAssertion consequent = new RelationAssertion(
                new SymbolId("entity:device"),
                "stop",
                new SymbolId("concept:true"),
                0.8
        );
        graph.addAssertion(antecedent);
        graph.addRule(new RuleAssertion(antecedent, consequent, 0.85));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals("stop")
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:device")));
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
