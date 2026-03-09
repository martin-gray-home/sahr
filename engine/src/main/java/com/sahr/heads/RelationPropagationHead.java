package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolicAttentionHead;
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RelationPropagationHead implements SymbolicAttentionHead {
    private static final String PREDICATE_AT = "at";
    private static final String PREDICATE_LOCATED_IN = "locatedIn";
    private static final String SAHR_COLOCATION = "https://sahr.ai/ontology/relations#colocation";

    private final Set<String> coLocationRelations;

    public RelationPropagationHead() {
        this(Set.of("wear", "wearing", "hold", "holding", "carry", "carrying",
                "with", "possess", "have", "opposite", "partOf", SAHR_COLOCATION));
    }

    public RelationPropagationHead(Set<String> coLocationRelations) {
        this.coLocationRelations = new HashSet<>(coLocationRelations);
    }

    @Override
    public String getName() {
        return "relation-propagation";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        List<ReasoningCandidate> candidates = new ArrayList<>();
        Set<String> expandedCoLocation = expandCoLocationPredicates(ontology);

        List<RelationAssertion> atAssertions = graph.findByPredicate(PREDICATE_AT);
        List<RelationAssertion> locatedAssertions = graph.findByPredicate(PREDICATE_LOCATED_IN);
        List<RelationAssertion> locationAssertions = new ArrayList<>(locatedAssertions);
        locationAssertions.addAll(atAssertions);

        for (RelationAssertion left : atAssertions) {
            for (RelationAssertion right : locatedAssertions) {
                if (!left.object().equals(right.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        left.subject(),
                        PREDICATE_LOCATED_IN,
                        right.object(),
                        averageConfidence(left.confidence(), right.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(inferred, List.of(left.toString(), right.toString()), 1, memory));
            }
        }

        for (RelationAssertion left : locatedAssertions) {
            for (RelationAssertion right : locatedAssertions) {
                if (!left.object().equals(right.subject())) {
                    continue;
                }
                RelationAssertion inferred = new RelationAssertion(
                        left.subject(),
                        PREDICATE_LOCATED_IN,
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
                        PREDICATE_LOCATED_IN,
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
                        PREDICATE_LOCATED_IN,
                        location.object(),
                        averageConfidence(relation.confidence(), location.confidence())
                );
                if (exists(graph, inferred)) {
                    continue;
                }
                candidates.add(buildCandidate(inferred, List.of(relation.toString(), location.toString()), 1, memory));
            }
        }

        return candidates;
    }

    private Set<String> expandCoLocationPredicates(OntologyService ontology) {
        Set<String> expanded = RelationPredicateAliases.withSahrIriAliases(coLocationRelations);
        for (String predicate : coLocationRelations) {
            if (!RelationPredicateAliases.isIri(predicate)) {
                continue;
            }
            expanded.addAll(ontology.getSubproperties(predicate));
        }
        addInversePredicates(ontology, expanded);
        return expanded;
    }

    private void addInversePredicates(OntologyService ontology, Set<String> expanded) {
        Set<String> snapshot = new HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(expanded::add);
        }
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

    private double memoryFocus(WorkingMemory memory, RelationAssertion assertion) {
        if (memory == null || assertion == null) {
            return 0.6;
        }
        if (memory.isActiveEntity(assertion.subject()) || memory.isActiveEntity(assertion.object())) {
            return 1.0;
        }
        return 0.6;
    }

    private double normalize(Iterable<Double> parts) {
        double total = 0.0;
        int count = 0;
        for (double part : parts) {
            total += part;
            count += 1;
        }
        return count == 0 ? 0.0 : Math.min(1.0, total / count);
    }

    private double averageConfidence(double left, double right) {
        return Math.min(1.0, (left + right) / 2.0);
    }
}
