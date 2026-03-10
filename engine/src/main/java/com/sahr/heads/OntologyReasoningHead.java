package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OntologyReasoningHead extends BaseHead {
    @Override
    public String getName() {
        return "ontology-reasoning";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Applies ontology symmetry, inverse, and transitive rules.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() == QueryGoal.Type.UNKNOWN) {
            return List.of();
        }

        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        List<ReasoningCandidate> candidates = new ArrayList<>();

        List<RelationAssertion> assertions = graph.getAllAssertions();
        for (RelationAssertion assertion : assertions) {
            inferSymmetric(assertion, ontology, graph, candidates);
            inferInverse(assertion, ontology, graph, candidates);
        }

        for (RelationAssertion left : assertions) {
            if (!ontology.isTransitiveProperty(left.predicate())) {
                continue;
            }
            for (RelationAssertion right : assertions) {
                if (!left.predicate().equals(right.predicate())) {
                    continue;
                }
                if (!left.object().equals(right.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        left.subject(),
                        left.predicate(),
                        right.object(),
                        averageConfidence(left.confidence(), right.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(
                        inferred,
                        List.of(left.toString(), right.toString(), "transitive:" + left.predicate()),
                        2
                ));
            }
        }

        return candidates;
    }

    private void inferSymmetric(RelationAssertion assertion,
                                OntologyService ontology,
                                KnowledgeBase graph,
                                List<ReasoningCandidate> candidates) {
        if (!ontology.isSymmetricProperty(assertion.predicate())) {
            return;
        }
        RelationAssertion inferred = new RelationAssertion(
                assertion.object(),
                assertion.predicate(),
                assertion.subject(),
                assertion.confidence()
        );
        if (exists(graph, inferred)) {
            return;
        }
        candidates.add(buildCandidate(
                inferred,
                List.of(assertion.toString(), "symmetric:" + assertion.predicate()),
                1
        ));
    }

    private void inferInverse(RelationAssertion assertion,
                              OntologyService ontology,
                              KnowledgeBase graph,
                              List<ReasoningCandidate> candidates) {
        Optional<String> inverse = ontology.getInverseProperty(assertion.predicate());
        if (inverse.isEmpty()) {
            return;
        }
        RelationAssertion inferred = new RelationAssertion(
                assertion.object(),
                inverse.get(),
                assertion.subject(),
                assertion.confidence()
        );
        if (exists(graph, inferred)) {
            return;
        }
        candidates.add(buildCandidate(
                inferred,
                List.of(assertion.toString(), "inverse:" + assertion.predicate()),
                1
        ));
    }

    private boolean exists(KnowledgeBase graph, RelationAssertion assertion) {
        return graph.getAllAssertions().stream().anyMatch(existing ->
                existing.subject().equals(assertion.subject())
                        && existing.predicate().equals(assertion.predicate())
                        && existing.object().equals(assertion.object()));
    }

    private ReasoningCandidate buildCandidate(RelationAssertion assertion, List<String> evidence, int depth) {
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("ontology_support", 1.0);
        breakdown.put("graph_confidence", assertion.confidence());
        double score = normalize(breakdown.values());

        return new ReasoningCandidate(
                CandidateType.ASSERTION,
                assertion,
                score,
                getName(),
                evidence,
                breakdown,
                depth
        );
    }

}
