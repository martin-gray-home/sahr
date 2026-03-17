package com.sahr.heads;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.sahr.support.HeadOntologyTestSupport;

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

        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        baseOntology.addPropertyRange("http://example.org/test#inside", "http://example.org/test#Place");
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("cat", "http://example.org/test#Place"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate ->
                "entity:cat inside entity:box".equals(candidate.payload())));
    }

    @Test
    void appliesPolicyBreakdownWhenPolicyProviderPresent() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId catId = new SymbolId("entity:cat");
        graph.addEntity(new EntityNode(catId, "cat", Set.of("cat")));
        graph.addAssertion(new RelationAssertion(
                catId,
                "http://example.org/test#inside",
                new SymbolId("entity:box"),
                0.8
        ));

        InMemoryOntologyService base = HeadOntologyTestSupport.createOntology();
        base.addPropertyRange("http://example.org/test#inside", "http://example.org/test#Place");
        OntologyService ontology = new PolicyStubOntology(base);

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.where("cat", "http://example.org/test#Place"),
                graph,
                ontology
        ));

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.scoreBreakdown() != null
                        && candidate.scoreBreakdown().containsKey("policy_rule_inverse")));
    }

    private static final class PolicyStubOntology implements OntologyService, PropertyPolicyProvider {
        private final InMemoryOntologyService delegate;

        private PolicyStubOntology(InMemoryOntologyService delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isSubclassOf(String child, String parent) {
            return delegate.isSubclassOf(child, parent);
        }

        @Override
        public boolean isSymmetricProperty(String property) {
            return delegate.isSymmetricProperty(property);
        }

        @Override
        public boolean isTransitiveProperty(String property) {
            return delegate.isTransitiveProperty(property);
        }

        @Override
        public Optional<String> getInverseProperty(String property) {
            return delegate.getInverseProperty(property);
        }

        @Override
        public Set<String> getSuperclasses(String concept) {
            return delegate.getSuperclasses(concept);
        }

        @Override
        public Set<String> getSubclasses(String concept) {
            return delegate.getSubclasses(concept);
        }

        @Override
        public Set<String> getSubproperties(String property) {
            return delegate.getSubproperties(property);
        }

        @Override
        public Set<String> getObjectPropertyRanges(String property) {
            return delegate.getObjectPropertyRanges(property);
        }

        @Override
        public Set<String> getObjectPropertiesByLabel(String label) {
            return delegate.getObjectPropertiesByLabel(label);
        }

        @Override
        public Set<String> getEntityIrisByLabel(String label) {
            return delegate.getEntityIrisByLabel(label);
        }

        @Override
        public Set<String> getLabels(String iri) {
            return delegate.getLabels(iri);
        }

        @Override
        public Optional<String> getAnnotationValue(String iri, String annotationIri) {
            return delegate.getAnnotationValue(iri, annotationIri);
        }

        @Override
        public Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
            return delegate.getEntitiesWithAnnotation(annotationIri, value);
        }

        @Override
        public Set<String> getObjectPropertyTargets(String subjectIri, String propertyIri) {
            return delegate.getObjectPropertyTargets(subjectIri, propertyIri);
        }

        @Override
        public Optional<InferencePolicy> inversePolicy(String propertyIri) {
            return Optional.of(new InferencePolicy(InferencePolicyStrength.SOFT, true, "test policy"));
        }

        @Override
        public Optional<InferencePolicy> symmetricPolicy(String propertyIri) {
            return Optional.empty();
        }

        @Override
        public Optional<InferencePolicy> transitivePolicy(String propertyIri) {
            return Optional.empty();
        }

        @Override
        public Optional<String> inverseProperty(String propertyIri) {
            return delegate.getInverseProperty(propertyIri);
        }
    }
}
