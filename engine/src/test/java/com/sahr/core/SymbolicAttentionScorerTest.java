package com.sahr.core;

import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolicAttentionScorerTest {
    @Test
    void prefersExpectedTypeMatchForRelationAnswers() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
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
    void usesNeutralQueryMatchForNonAnswerCandidates() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        SymbolicAttentionScorer scorer = new SymbolicAttentionScorer();

        QueryGoal query = QueryGoal.where("concept:hat", "concept:location");
        HeadContext context = new HeadContext(query, graph, ontology);

        ReasoningCandidate assertionCandidate = new ReasoningCandidate(
                CandidateType.ASSERTION,
                new RelationAssertion(
                        new SymbolId("entity:hat"),
                        "locatedIn",
                        new SymbolId("entity:room"),
                        0.9
                ),
                0.7,
                "test-head",
                List.of("entity:hat locatedIn entity:room"),
                java.util.Map.of("graph_confidence", 0.9),
                1
        );

        SymbolicAttentionScorer.QueryMatchResult match = scorer.score(context, assertionCandidate);
        assertTrue(Math.abs(match.queryMatchScore() - 0.5) < 0.0001);
    }
}
