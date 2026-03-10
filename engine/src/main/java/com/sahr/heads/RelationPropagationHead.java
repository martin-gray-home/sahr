package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.HeadOntology;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RelationPropagationHead extends BaseHead {
    public RelationPropagationHead() {
    }

    @Override
    public String getName() {
        return "relation-propagation";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Propagates location through co-location and location-transfer relations.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        List<ReasoningCandidate> candidates = new ArrayList<>();
        Set<String> expandedCoLocation = HeadOntology.expandFamilyWithInverses(ontology, HeadOntology.COLOCATION);
        Set<String> locationPredicates = HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER);
        if (locationPredicates.isEmpty()) {
            return List.of();
        }
        List<RelationAssertion> locationAssertions = new ArrayList<>();
        java.util.Map<SymbolId, List<RelationAssertion>> locationTargets = new java.util.HashMap<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (locationPredicates.contains(assertion.predicate())) {
                locationAssertions.add(assertion);
                locationTargets.computeIfAbsent(assertion.object(), ignored -> new ArrayList<>()).add(assertion);
            }
        }
        String preferredLocationPredicate = pickLocationPredicate(locationPredicates);

        for (RelationAssertion left : locationAssertions) {
            for (RelationAssertion right : locationAssertions) {
                if (!left.object().equals(right.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        left.subject(),
                        right.predicate(),
                        right.object(),
                        averageConfidence(left.confidence(), right.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(inferred, List.of(left.toString(), right.toString()), 2, memory));
            }
        }

        for (RelationAssertion relation : graph.getAllAssertions()) {
            if (!expandedCoLocation.contains(relation.predicate())) {
                continue;
            }
            for (RelationAssertion location : locationAssertions) {
                if (!relation.subject().equals(location.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        relation.object(),
                        location.predicate(),
                        location.object(),
                        averageConfidence(relation.confidence(), location.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1, memory));
            }
        }

        for (RelationAssertion relation : graph.getAllAssertions()) {
            if (!expandedCoLocation.contains(relation.predicate())) {
                continue;
            }
            for (RelationAssertion location : locationAssertions) {
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
                candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1, memory));
            }
        }

        if (preferredLocationPredicate != null && !preferredLocationPredicate.isBlank()) {
            for (RelationAssertion relation : graph.getAllAssertions()) {
                if (!expandedCoLocation.contains(relation.predicate())) {
                    continue;
                }
                List<RelationAssertion> subjectLocations = locationTargets.get(relation.subject());
                if (subjectLocations != null) {
                    for (RelationAssertion location : subjectLocations) {
                        RelationAssertion inferred = new RelationAssertion(
                                relation.object(),
                                preferredLocationPredicate,
                                relation.subject(),
                                averageConfidence(relation.confidence(), location.confidence())
                        );
                        if (exists(graph, inferred)) {
                            continue;
                        }
                        candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1, memory));
                    }
                }
                List<RelationAssertion> objectLocations = locationTargets.get(relation.object());
                if (objectLocations != null) {
                    for (RelationAssertion location : objectLocations) {
                        RelationAssertion inferred = new RelationAssertion(
                                relation.subject(),
                                preferredLocationPredicate,
                                relation.object(),
                                averageConfidence(relation.confidence(), location.confidence())
                        );
                        if (exists(graph, inferred)) {
                            continue;
                        }
                        candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1, memory));
                    }
                }
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

    private ReasoningCandidate buildCandidate(RelationAssertion assertion, List<String> evidence, int depth, WorkingMemory memory) {
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("ontology_support", 0.4);
        breakdown.put("graph_confidence", assertion.confidence());
        double memoryFocus = memoryFocus(memory, assertion);
        breakdown.put("working_memory_focus", memoryFocus);
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

    private String pickLocationPredicate(Set<String> locationPredicates) {
        if (locationPredicates == null || locationPredicates.isEmpty()) {
            return null;
        }
        String preferred = null;
        for (String predicate : locationPredicates) {
            if (predicate == null) {
                continue;
            }
            if (predicate.endsWith("locatedIn")) {
                return predicate;
            }
            if (preferred == null || predicate.compareTo(preferred) < 0) {
                preferred = predicate;
            }
        }
        return preferred;
    }

    private double memoryFocus(WorkingMemory memory, RelationAssertion assertion) {
        if (memory == null || assertion == null) {
            return 0.6;
        }
        if (memory.isActiveEntity(assertion.subject()) || memory.isActiveEntity(assertion.object())) {
            return 1.0;
        }
        return 0.6;
    }

}
