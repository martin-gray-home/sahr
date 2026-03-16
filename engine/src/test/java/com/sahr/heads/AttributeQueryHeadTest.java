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
import com.sahr.core.WorkingMemory;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import com.sahr.support.HeadOntologyTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeQueryHeadTest {
    private final AttributeQueryHead head = new AttributeQueryHead();

    @Test
    void emitsPolicyBreakdownWhenPolicyProviderPresent() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        OntologyService ontology = new PolicyStubOntology(baseOntology);

        SymbolId box = new SymbolId("entity:box");
        graph.addEntity(new EntityNode(box, "box", Set.of("concept:box")));
        graph.addAssertion(new RelationAssertion(box, "hasAttribute", new SymbolId("entity:red"), 0.9));

        List<ReasoningCandidate> candidates = head.evaluate(new HeadContext(
                QueryGoal.attribute("entity:box", "color"),
                graph,
                ontology,
                new WorkingMemory()
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
