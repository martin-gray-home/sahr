package com.sahr.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sahr.ontology.InMemoryOntologyService;

class SahrReasonerAttentionTest {
    @Test
    void softmaxNormalizesCandidateScores() {
        SymbolicAttentionHead head = new SymbolicAttentionHead() {
            @Override
            public String getName() {
                return "test-head";
            }

            @Override
            public List<ReasoningCandidate> evaluate(HeadContext context) {
                ReasoningCandidate first = new ReasoningCandidate(
                        CandidateType.ANSWER,
                        "a",
                        0.9,
                        getName(),
                        List.of("a locatedIn room"),
                        Map.of("graph_confidence", 0.9),
                        0
                );
                ReasoningCandidate second = new ReasoningCandidate(
                        CandidateType.ANSWER,
                        "b",
                        0.1,
                        getName(),
                        List.of("b locatedIn room"),
                        Map.of("graph_confidence", 0.1),
                        0
                );
                return List.of(first, second);
            }
        };

        SahrReasoner reasoner = new SahrReasoner(List.of(head));
        HeadContext context = new HeadContext(QueryGoal.unknown(), new InMemoryKnowledgeBase(), new InMemoryOntologyService());

        List<ReasoningCandidate> results = reasoner.reason(context);
        double sum = results.stream().mapToDouble(ReasoningCandidate::score).sum();

        assertTrue(Math.abs(sum - 1.0) < 0.0001);
        assertTrue(results.get(0).score() > results.get(1).score());
    }
}
