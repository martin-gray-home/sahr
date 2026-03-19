package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;
import com.sahr.core.RuleAssertion;
import com.sahr.core.EntityNode;
import com.sahr.core.RuleAtom;
import com.sahr.core.RuleFrame;
import com.sahr.core.RuleTerm;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import com.sahr.support.OwlOntologyTestSupport;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    void emitsPolicyBreakdownWhenPolicyProviderPresent() throws Exception {
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

        OntologyService ontology = new PolicyStubOntology(new InMemoryOntologyService());
        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontology);
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.scoreBreakdown() != null
                        && candidate.scoreBreakdown().containsKey("policy_rule_inverse")));
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

    @Test
    void appliesQuantifiedRuleFramesOverAssertions() throws Exception {
        OWLOntology ontology = loadTestOntology();
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addEntity(new EntityNode(new SymbolId("entity:hat"), "hat", Set.of("hat")));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "rdf:type",
                new SymbolId("concept:hat"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "in",
                new SymbolId("entity:house"),
                0.9
        ));

        RuleFrame rule = new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "rdf:type", RuleTerm.constant("concept:hat")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "hasAttribute", RuleTerm.constant("concept:green")),
                0.85
        );
        graph.addRuleFrame(rule);

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals("hasAttribute")
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:hat")
                        && ((RelationAssertion) candidate.payload()).object().value().equals("concept:green")));
    }

    @Test
    void appliesTwoVariableQuantifiedRuleFramesOverAssertions() throws Exception {
        OWLOntology ontology = loadTestOntology();
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addEntity(new EntityNode(new SymbolId("entity:hat"), "hat", Set.of("hat")));
        graph.addEntity(new EntityNode(new SymbolId("entity:man"), "man", Set.of("man")));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                "ownedBy",
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "in",
                new SymbolId("entity:house"),
                0.95
        ));

        RuleFrame rule = new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "ownedBy", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house")),
                0.85
        );
        graph.addRuleFrame(rule);

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals("in")
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:hat")
                        && ((RelationAssertion) candidate.payload()).object().value().equals("entity:house")));
    }

    @Test
    void infersContainmentFromSurfaceContactBridge() throws Exception {
        List<OntologyHeadDefinition> definitions = OwlOntologyTestSupport.buildHeadDefinitions();
        OntologyDefinedHead head = new OntologyDefinedHead(definitions);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:cat"),
                "https://sahr.ai/ontology/relations#on",
                new SymbolId("entity:mat"),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:mat"),
                "https://sahr.ai/ontology/relations#in",
                new SymbolId("entity:house"),
                0.95
        ));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, new InMemoryOntologyService());
        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().equals("https://sahr.ai/ontology/relations#in")
                        && ((RelationAssertion) candidate.payload()).subject().value().equals("entity:cat")
                        && ((RelationAssertion) candidate.payload()).object().value().equals("entity:house")));
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
