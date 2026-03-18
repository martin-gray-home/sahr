package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.QueryResult;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphRetrievalHeadTest {
    @Test
    void resolvesNestedLocationChain() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
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

        assertTrue(candidates.stream().anyMatch(candidate -> {
            if (!(candidate.payload() instanceof QueryResult result)) {
                return false;
            }
            return result.facts().stream().anyMatch(fact ->
                    "entity:apple".equals(fact.subject().value())
                            && "entity:basket".equals(fact.object().value())
                            && ("inside".equals(localName(fact.predicate()))
                            || "in".equals(localName(fact.predicate()))));
        }));
    }

    @Test
    void appliesInversePolicyBreakdownForWhere() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        OntologyService ontology = new PolicyStubOntology(baseOntology);
        GraphRetrievalHead head = new GraphRetrievalHead();

        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:apple"),
                "inside",
                new SymbolId("entity:basket"),
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
                candidate.scoreBreakdown() != null
                        && candidate.scoreBreakdown().containsKey("policy_rule_inverse")));
    }

    private static String localName(String predicate) {
        if (predicate == null) {
            return "";
        }
        int hashIdx = predicate.lastIndexOf('#');
        int slashIdx = predicate.lastIndexOf('/');
        int idx = Math.max(hashIdx, slashIdx);
        return (idx >= 0 ? predicate.substring(idx + 1) : predicate).toLowerCase(java.util.Locale.ROOT);
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
        public java.util.Set<String> getSuperclasses(String concept) {
            return delegate.getSuperclasses(concept);
        }

        @Override
        public java.util.Set<String> getSubclasses(String concept) {
            return delegate.getSubclasses(concept);
        }

        @Override
        public java.util.Set<String> getSubproperties(String property) {
            return delegate.getSubproperties(property);
        }

        @Override
        public java.util.Set<String> getObjectPropertyRanges(String property) {
            return delegate.getObjectPropertyRanges(property);
        }

        @Override
        public java.util.Set<String> getObjectPropertiesByLabel(String label) {
            return delegate.getObjectPropertiesByLabel(label);
        }

        @Override
        public java.util.Set<String> getEntityIrisByLabel(String label) {
            return delegate.getEntityIrisByLabel(label);
        }

        @Override
        public java.util.Set<String> getLabels(String iri) {
            return delegate.getLabels(iri);
        }

        @Override
        public Optional<String> getAnnotationValue(String iri, String annotationIri) {
            return delegate.getAnnotationValue(iri, annotationIri);
        }

        @Override
        public java.util.Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
            return delegate.getEntitiesWithAnnotation(annotationIri, value);
        }

        @Override
        public java.util.Set<String> getObjectPropertyTargets(String subjectIri, String propertyIri) {
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
