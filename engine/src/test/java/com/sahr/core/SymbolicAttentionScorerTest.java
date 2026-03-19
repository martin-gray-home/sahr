package com.sahr.core;

import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolicAttentionScorerTest {
    @Test
    void boostsCandidatesAlignedWithWorkingMemoryWhenHeadDidNotAlreadyScoreMemory() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();
        WorkingMemory workingMemory = new WorkingMemory();

        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");
        SymbolId coat = new SymbolId("entity:coat");

        workingMemory.addActiveEntity(man);
        workingMemory.recordAssertion(new RelationAssertion(man, "wear", hat, 0.9));

        QueryGoal query = QueryGoal.relation("entity:man", "wear", null, null);
        HeadContext context = new HeadContext(query, graph, ontology, workingMemory);

        ReasoningCandidate hatCandidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                hat,
                0.9,
                "test-head",
                List.of("entity:man wear entity:hat"),
                java.util.Map.of("graph_confidence", 0.9),
                0
        );
        ReasoningCandidate coatCandidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                coat,
                0.9,
                "test-head",
                List.of("entity:woman wear entity:coat"),
                java.util.Map.of("graph_confidence", 0.9),
                0
        );

        SymbolicAttentionScorer.QueryMatchResult focused = scorer.score(context, hatCandidate);
        SymbolicAttentionScorer.QueryMatchResult unfocused = scorer.score(context, coatCandidate);

        assertTrue(focused.queryMatchScore() > unfocused.queryMatchScore());
        assertTrue(focused.breakdown(0.9, 0.9).get("attention_working_memory_focus")
                > unfocused.breakdown(0.9, 0.9).get("attention_working_memory_focus"));
    }

    @Test
    void doesNotDoubleCountWhenHeadAlreadyProvidesWorkingMemoryFocus() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();
        WorkingMemory workingMemory = new WorkingMemory();

        workingMemory.addActiveEntity(new SymbolId("entity:man"));
        QueryGoal query = QueryGoal.relation("entity:man", "wear", null, null);
        HeadContext context = new HeadContext(query, graph, ontology, workingMemory);

        ReasoningCandidate candidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                new SymbolId("entity:hat"),
                0.9,
                "test-head",
                List.of("entity:man wear entity:hat"),
                java.util.Map.of("working_memory_focus", 1.0, "graph_confidence", 0.9),
                0
        );

        SymbolicAttentionScorer.QueryMatchResult match = scorer.score(context, candidate);

        assertEquals(1.0, match.breakdown(0.9, 0.9).get("attention_working_memory_focus"));
    }

    @Test
    void scoresAttributeAnswersUsingSharedWorkingMemoryAttention() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();
        WorkingMemory workingMemory = new WorkingMemory();

        SymbolId hat = new SymbolId("entity:hat");
        workingMemory.addActiveEntity(hat);

        QueryGoal query = QueryGoal.attribute("entity:hat", "color");
        HeadContext context = new HeadContext(query, graph, ontology, workingMemory);

        ReasoningCandidate focused = new ReasoningCandidate(
                CandidateType.ANSWER,
                "green",
                0.8,
                "test-head",
                List.of("entity:hat hasAttribute entity:green"),
                java.util.Map.of("graph_confidence", 0.8),
                0
        );
        ReasoningCandidate unfocused = new ReasoningCandidate(
                CandidateType.ANSWER,
                "green",
                0.8,
                "test-head",
                List.of("entity:coat hasAttribute entity:green"),
                java.util.Map.of("graph_confidence", 0.8),
                0
        );

        SymbolicAttentionScorer.QueryMatchResult focusedMatch = scorer.score(context, focused);
        SymbolicAttentionScorer.QueryMatchResult unfocusedMatch = scorer.score(context, unfocused);

        assertTrue(focusedMatch.queryMatchScore() > unfocusedMatch.queryMatchScore());
    }

    @Test
    void scoresSubgoalCandidatesAgainstGoalStackAndWorkingMemory() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();
        WorkingMemory workingMemory = new WorkingMemory();

        QueryGoal current = QueryGoal.relation("entity:man", "wear", null, null);
        workingMemory.pushGoal(current);
        workingMemory.addActiveEntity(new SymbolId("entity:man"));

        HeadContext context = new HeadContext(current, graph, ontology, workingMemory);

        ReasoningCandidate aligned = new ReasoningCandidate(
                CandidateType.SUBGOAL,
                QueryGoal.relation("entity:man", "wear", null, null).withParent(current.goalId(), current.depth() + 1),
                0.7,
                "test-head",
                List.of("entity:man wear entity:hat"),
                java.util.Map.of("query_score", 0.7),
                1
        );
        ReasoningCandidate unrelated = new ReasoningCandidate(
                CandidateType.SUBGOAL,
                QueryGoal.relation("entity:woman", "carry", null, null).withParent(current.goalId(), current.depth() + 1),
                0.7,
                "test-head",
                List.of("entity:woman carry entity:box"),
                java.util.Map.of("query_score", 0.7),
                1
        );

        SymbolicAttentionScorer.QueryMatchResult alignedMatch = scorer.score(context, aligned);
        SymbolicAttentionScorer.QueryMatchResult unrelatedMatch = scorer.score(context, unrelated);

        assertTrue(alignedMatch.queryMatchScore() > unrelatedMatch.queryMatchScore());
        assertTrue(alignedMatch.breakdown(0.7, 0.7).get("attention_goal_stack_focus")
                > unrelatedMatch.breakdown(0.7, 0.7).get("attention_goal_stack_focus"));
    }

    @Test
    void prefersExpectedTypeMatchForRelationAnswers() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();

        SymbolId hat = new SymbolId("entity:hat");
        SymbolId woman = new SymbolId("entity:woman");
        graph.addEntity(new EntityNode(hat, "hat", java.util.Set.of("concept:hat")));
        graph.addEntity(new EntityNode(woman, "woman", java.util.Set.of("concept:person")));

        QueryGoal query = QueryGoal.relation("entity:hat", "with", null, "concept:person");
        HeadContext context = new HeadContext(query, graph, ontology);

        ReasoningCandidate hatCandidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                hat,
                0.9,
                "test-head",
                List.of("entity:hat with entity:woman"),
                java.util.Map.of("graph_confidence", 0.9),
                0
        );
        ReasoningCandidate womanCandidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                woman,
                0.9,
                "test-head",
                List.of("entity:hat with entity:woman"),
                java.util.Map.of("graph_confidence", 0.9),
                0
        );

        SymbolicAttentionScorer.QueryMatchResult hatMatch = scorer.score(context, hatCandidate);
        SymbolicAttentionScorer.QueryMatchResult womanMatch = scorer.score(context, womanCandidate);

        assertTrue(womanMatch.queryMatchScore() > hatMatch.queryMatchScore());
    }

    @Test
    void usesNeutralQueryMatchForUnsupportedNonAnswerCandidates() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();

        QueryGoal query = QueryGoal.where("concept:hat", "concept:location");
        HeadContext context = new HeadContext(query, graph, ontology);

        ReasoningCandidate assertionCandidate = new ReasoningCandidate(
                CandidateType.ACTION,
                "look",
                0.7,
                "test-head",
                List.of("entity:hat locatedIn entity:room"),
                java.util.Map.of("graph_confidence", 0.9),
                1
        );

        SymbolicAttentionScorer.QueryMatchResult match = scorer.score(context, assertionCandidate);
        assertTrue(Math.abs(match.queryMatchScore() - 0.5) < 0.0001);
    }

    @Test
    void addsPolicyBreakdownForInverseMatch() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService base = HeadOntologyTestSupport.createOntology();
        OntologyService ontology = new PolicyStubOntology(base);
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();

        SymbolId man = new SymbolId("entity:man");
        SymbolId woman = new SymbolId("entity:woman");
        graph.addEntity(new EntityNode(man, "man", java.util.Set.of("concept:person")));
        graph.addEntity(new EntityNode(woman, "woman", java.util.Set.of("concept:person")));

        String wornBy = "https://sahr.ai/ontology/relations#wornBy";
        String wear = "https://sahr.ai/ontology/relations#wear";
        QueryGoal query = QueryGoal.relation("entity:man", wornBy, null, null);
        HeadContext context = new HeadContext(query, graph, ontology);

        ReasoningCandidate candidate = new ReasoningCandidate(
                CandidateType.ANSWER,
                woman,
                0.9,
                "test-head",
                List.of("entity:woman " + wear + " entity:man"),
                java.util.Map.of("graph_confidence", 0.9),
                0
        );

        SymbolicAttentionScorer.QueryMatchResult match = scorer.score(context, candidate);
        java.util.Map<String, Double> breakdown = match.breakdown(0.9, 0.9);
        assertTrue(breakdown.containsKey("policy_rule_inverse"));
        assertTrue(breakdown.containsKey("policy_strength"));
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
