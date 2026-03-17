package com.sahr.semantic.policy;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.heads.RelationQueryHead;
import com.sahr.ontology.CachedOntologyService;
import com.sahr.ontology.OwlApiOntologyService;
import com.sahr.semantic.importer.OwlAlignmentPipeline;
import com.sahr.semantic.importer.OwlAlignmentResult;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEndToEndTest {
    private static final String NEAR = "https://sahr.ai/ontology/relations#near";
    private static final String NEAR_INVERSE = "https://sahr.ai/ontology/relations#nearInverse";

    @Test
    void appliesSymmetricPolicyFromAlignedOntology() throws Exception {
        OntologyService ontology = buildPolicyAwareOntology();
        RelationQueryHead head = new RelationQueryHead();

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId alice = new SymbolId("entity:alice");
        SymbolId bob = new SymbolId("entity:bob");
        graph.addEntity(new EntityNode(alice, "alice", Set.of("concept:person")));
        graph.addEntity(new EntityNode(bob, "bob", Set.of("concept:person")));
        graph.addAssertion(new RelationAssertion(bob, NEAR, alice, 0.9));

        QueryGoal query = QueryGoal.relation("entity:alice", NEAR, null, null);
        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(query, graph, ontology));

        assertTrue(candidates.stream().anyMatch(candidate ->
                bob.equals(candidate.payload())
                        && hasPolicyRule(candidate, "policy_rule_symmetric")));
    }

    @Test
    void appliesInversePolicyFromAlignedOntology() throws Exception {
        OntologyService ontology = buildPolicyAwareOntology();
        RelationQueryHead head = new RelationQueryHead();

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId alice = new SymbolId("entity:alice");
        SymbolId bob = new SymbolId("entity:bob");
        graph.addEntity(new EntityNode(alice, "alice", Set.of("concept:person")));
        graph.addEntity(new EntityNode(bob, "bob", Set.of("concept:person")));
        graph.addAssertion(new RelationAssertion(bob, NEAR_INVERSE, alice, 0.9));

        QueryGoal query = QueryGoal.relation("entity:alice", NEAR, null, null);
        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(query, graph, ontology));

        assertTrue(candidates.stream().anyMatch(candidate ->
                bob.equals(candidate.payload())
                        && hasPolicyRule(candidate, "policy_rule_inverse")));
    }

    private OntologyService buildPolicyAwareOntology() throws Exception {
        OWLOntology ontology = loadTestOntology();
        OwlAlignmentPipeline pipeline = OwlAlignmentPipeline.defaultPipeline();
        OwlAlignmentResult aligned = pipeline.run(ontology, "policy-e2e");
        PropertyPolicyRegistry registry = PropertyPolicyRegistry.fromDecisions(aligned.propertyPolicyDecisions());
        OntologyService policyAware = new PolicyAwareOntologyService(new OwlApiOntologyService(ontology), registry);
        return new CachedOntologyService(policyAware);
    }

    private OWLOntology loadTestOntology() throws Exception {
        try (InputStream stream = PolicyEndToEndTest.class.getClassLoader()
                .getResourceAsStream("ontology/test-owl-ingest.ttl")) {
            if (stream == null) {
                throw new IllegalStateException("Missing test ontology resource.");
            }
            var manager = OWLManager.createOWLOntologyManager();
            return manager.loadOntologyFromOntologyDocument(new StreamDocumentSource(stream));
        }
    }

    private boolean hasPolicyRule(ReasoningCandidate candidate, String key) {
        if (candidate == null || candidate.scoreBreakdown() == null) {
            return false;
        }
        return candidate.scoreBreakdown().containsKey(key);
    }
}
