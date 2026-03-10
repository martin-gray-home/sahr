package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.HeadOntology;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SurfaceContactPropagationHead extends BaseHead {
    @Override
    public String getName() {
        return "surface-contact-propagation";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Propagates surface-contact relations (on/under) into location assertions.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        List<ReasoningCandidate> candidates = new ArrayList<>();

        Set<String> surfacePredicates = HeadOntology.expandFamilyWithInverses(ontology, HeadOntology.SURFACE_CONTACT);
        Set<String> locationPredicates = HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER);
        if (surfacePredicates.isEmpty() || locationPredicates.isEmpty()) {
            return List.of();
        }
        List<RelationAssertion> locatedAssertions = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (locationPredicates.contains(assertion.predicate())) {
                locatedAssertions.add(assertion);
            }
        }

        for (RelationAssertion relation : graph.getAllAssertions()) {
            if (!surfacePredicates.contains(relation.predicate())) {
                continue;
            }
            for (RelationAssertion location : locatedAssertions) {
                if (!relation.object().equals(location.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        relation.subject(),
                        location.predicate(),
                        location.object(),
                        averageConfidence(relation.confidence(), location.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1));
            }
        }

        return candidates;
    }

    private boolean exists(KnowledgeBase graph, RelationAssertion assertion) {
        return graph.getAllAssertions().stream().anyMatch(existing ->
                existing.subject().equals(assertion.subject())
                        && existing.predicate().equals(assertion.predicate())
                        && existing.object().equals(assertion.object()));
    }

    private ReasoningCandidate buildCandidate(RelationAssertion assertion, List<String> evidence, int depth) {
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("ontology_support", 0.6);
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
