package com.sahr.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        HeadContext context = new HeadContext(QueryGoal.unknown(), new InMemoryKnowledgeBase(), HeadOntologyTestSupport.createPolicyOntology());

        List<ReasoningCandidate> results = reasoner.reason(context);
        double sum = results.stream().mapToDouble(ReasoningCandidate::score).sum();

        assertTrue(Math.abs(sum - 1.0) < 0.0001);
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    void workingMemoryFocusBreaksTiesForOtherwiseEquivalentCandidates() {
        SymbolicAttentionHead head = new SymbolicAttentionHead() {
            @Override
            public String getName() {
                return "test-head";
            }

            @Override
            public List<ReasoningCandidate> evaluate(HeadContext context) {
                ReasoningCandidate focused = new ReasoningCandidate(
                        CandidateType.ANSWER,
                        "hat",
                        0.8,
                        getName(),
                        List.of("entity:man wear entity:hat"),
                        Map.of("graph_confidence", 0.8),
                        0
                );
                ReasoningCandidate unfocused = new ReasoningCandidate(
                        CandidateType.ANSWER,
                        "coat",
                        0.8,
                        getName(),
                        List.of("entity:woman wear entity:coat"),
                        Map.of("graph_confidence", 0.8),
                        0
                );
                return List.of(unfocused, focused);
            }
        };

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.addActiveEntity(new SymbolId("entity:man"));
        workingMemory.recordAssertion(new RelationAssertion(
                new SymbolId("entity:man"),
                "wear",
                new SymbolId("entity:hat"),
                0.9
        ));

        SahrReasoner reasoner = new SahrReasoner(List.of(head));
        HeadContext context = new HeadContext(
                QueryGoal.relation("entity:man", "wear", null, null),
                new InMemoryKnowledgeBase(),
                HeadOntologyTestSupport.createPolicyOntology(),
                workingMemory
        );

        List<ReasoningCandidate> results = reasoner.reason(context);

        assertEquals("hat", results.get(0).payload());
        assertTrue(results.get(0).score() > results.get(1).score());
    }
}
