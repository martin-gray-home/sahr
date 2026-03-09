package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.SymbolicAttentionHead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContainmentPropagationHead implements SymbolicAttentionHead {
    private static final String PREDICATE_LOCATED_IN = "locatedIn";
    private static final String CONTAINMENT_IRI = "https://sahr.ai/ontology/relations#containment";
    private static final String PREDICATE_INSIDE = "inside";
    private static final String PREDICATE_IN = "in";
    private static final int MAX_CHAIN_DEPTH = 6;

    @Override
    public String getName() {
        return "containment-propagation";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        QueryGoal query = context.query();
        List<ReasoningCandidate> candidates = new ArrayList<>();

        Set<String> containmentPredicates = expandContainmentPredicates(ontology);
        containmentPredicates.add(PREDICATE_LOCATED_IN);
        containmentPredicates.add(PREDICATE_INSIDE);
        containmentPredicates.add(PREDICATE_IN);

        Map<SymbolId, List<RelationAssertion>> adjacency = buildAdjacency(graph, containmentPredicates);
        Set<String> emitted = new HashSet<>();
        for (Map.Entry<SymbolId, List<RelationAssertion>> entry : adjacency.entrySet()) {
            SymbolId subject = entry.getKey();
            for (RelationAssertion relation : entry.getValue()) {
                List<RelationAssertion> path = new ArrayList<>();
                path.add(relation);
                Set<SymbolId> visited = new HashSet<>();
                visited.add(subject);
                visited.add(relation.object());
                walkContainment(subject, relation.object(), adjacency, path, visited, candidates, emitted, query, graph, ontology);
            }
        }

        return candidates;
    }

    private Set<String> expandContainmentPredicates(OntologyService ontology) {
        Set<String> expanded = new HashSet<>();
        expanded.add(CONTAINMENT_IRI);
        expanded.addAll(ontology.getSubproperties(CONTAINMENT_IRI));
        addInversePredicates(ontology, expanded);
        return expanded;
    }

    private Map<SymbolId, List<RelationAssertion>> buildAdjacency(KnowledgeBase graph, Set<String> predicates) {
        Map<SymbolId, List<RelationAssertion>> adjacency = new HashMap<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!predicates.contains(assertion.predicate())) {
                continue;
            }
            adjacency.computeIfAbsent(assertion.subject(), key -> new ArrayList<>()).add(assertion);
        }
        return adjacency;
    }

    private void walkContainment(SymbolId root,
                                 SymbolId current,
                                 Map<SymbolId, List<RelationAssertion>> adjacency,
                                 List<RelationAssertion> path,
                                 Set<SymbolId> visited,
                                 List<ReasoningCandidate> candidates,
                                 Set<String> emitted,
                                 QueryGoal query,
                                 KnowledgeBase graph,
                                 OntologyService ontology) {
        if (path.size() >= MAX_CHAIN_DEPTH) {
            emitCandidate(root, current, path, candidates, emitted, query, graph, ontology);
            return;
        }

        List<RelationAssertion> nextEdges = adjacency.getOrDefault(current, List.of());
        if (nextEdges.isEmpty()) {
            emitCandidate(root, current, path, candidates, emitted, query, graph, ontology);
            return;
        }

        for (RelationAssertion next : nextEdges) {
            if (!visited.add(next.object())) {
                continue;
            }
            path.add(next);
            walkContainment(root, next.object(), adjacency, path, visited, candidates, emitted, query, graph, ontology);
            path.remove(path.size() - 1);
            visited.remove(next.object());
        }
    }

    private void emitCandidate(SymbolId subject,
                               SymbolId location,
                               List<RelationAssertion> path,
                               List<ReasoningCandidate> candidates,
                               Set<String> emitted,
                               QueryGoal query,
                               KnowledgeBase graph,
                               OntologyService ontology) {
        String key = subject.value() + "|" + location.value();
        if (!emitted.add(key)) {
            return;
        }
        double confidence = averageConfidence(path);
        if (path.size() > 1) {
            confidence = Math.min(1.0, confidence + 0.05 * (path.size() - 1));
        }
        RelationAssertion inferred = new RelationAssertion(subject, PREDICATE_LOCATED_IN, location, confidence);
        candidates.add(buildCandidate(inferred, buildEvidence(path), path.size()));
        if (query != null && query.type() == QueryGoal.Type.WHERE) {
            if (matchesType(graph, ontology, subject, query.entityType())) {
                candidates.add(buildAnswerCandidate(inferred, path));
            }
        }
    }

    private List<String> buildEvidence(List<RelationAssertion> path) {
        List<String> evidence = new ArrayList<>(path.size());
        for (RelationAssertion assertion : path) {
            evidence.add(assertion.toString());
        }
        return evidence;
    }

    private void addInversePredicates(OntologyService ontology, Set<String> expanded) {
        Set<String> snapshot = new HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(expanded::add);
        }
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

    private ReasoningCandidate buildAnswerCandidate(RelationAssertion assertion, List<RelationAssertion> path) {
        double depthBoost = Math.max(0.0, 0.1 * (path.size() - 1));
        double score = Math.min(1.0, assertion.confidence() + depthBoost);
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("graph_confidence", assertion.confidence());
        breakdown.put("depth_boost", depthBoost);
        breakdown.put("ontology_support", 0.6);
        String answer = assertion.subject() + " " + assertion.predicate() + " " + assertion.object();

        return new ReasoningCandidate(
                CandidateType.ANSWER,
                answer,
                score,
                getName(),
                buildEvidence(path),
                breakdown,
                path.size()
        );
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

    private double averageConfidence(List<RelationAssertion> assertions) {
        if (assertions.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RelationAssertion assertion : assertions) {
            total += assertion.confidence();
        }
        return Math.min(1.0, total / assertions.size());
    }

    private boolean matchesType(KnowledgeBase graph, OntologyService ontology, SymbolId subject, String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        String normalizedRequested = normalizeTypeToken(requestedType);
        if (normalizeTypeToken(subject.value()).equals(normalizedRequested)) {
            return true;
        }
        return graph.findEntity(subject)
                .map(EntityNode::conceptTypes)
                .map(types -> types.stream().anyMatch(type ->
                        type.equals(requestedType)
                                || normalizeTypeToken(type).equals(normalizedRequested)
                                || ontology.isSubclassOf(type, requestedType)))
                .orElse(false);
    }

    private String normalizeTypeToken(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.startsWith("concept:")) {
            return raw.substring("concept:".length());
        }
        if (raw.startsWith("entity:")) {
            return raw.substring("entity:".length());
        }
        return raw;
    }
}
