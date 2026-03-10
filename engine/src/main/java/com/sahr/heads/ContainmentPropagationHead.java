package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadOntology;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContainmentPropagationHead extends BaseHead {
    private static final int MAX_CHAIN_DEPTH = 6;

    @Override
    public String getName() {
        return "containment-propagation";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Propagates containment relations into location assertions.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        QueryGoal query = context.query();
        java.util.Optional<SymbolId> requestedEntity = resolveEntityFromQuery(query, graph);
        List<ReasoningCandidate> candidates = new ArrayList<>();

        Set<String> containmentPredicates = HeadOntology.expandFamilyWithInverses(ontology, HeadOntology.CONTAINMENT);
        if (containmentPredicates.isEmpty()) {
            return List.of();
        }

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
                walkContainment(subject, relation.object(), adjacency, path, visited, candidates, emitted, query, graph, ontology, requestedEntity);
            }
        }

        return candidates;
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
                                 OntologyService ontology,
                                 java.util.Optional<SymbolId> requestedEntity) {
        if (path.size() >= MAX_CHAIN_DEPTH) {
            emitCandidate(root, current, path, candidates, emitted, query, graph, ontology, requestedEntity);
            return;
        }

        List<RelationAssertion> nextEdges = adjacency.getOrDefault(current, List.of());
        if (nextEdges.isEmpty()) {
            emitCandidate(root, current, path, candidates, emitted, query, graph, ontology, requestedEntity);
            return;
        }

        for (RelationAssertion next : nextEdges) {
            if (!visited.add(next.object())) {
                continue;
            }
            path.add(next);
            walkContainment(root, next.object(), adjacency, path, visited, candidates, emitted, query, graph, ontology, requestedEntity);
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
                               OntologyService ontology,
                               java.util.Optional<SymbolId> requestedEntity) {
        String key = subject.value() + "|" + location.value();
        if (!emitted.add(key)) {
            return;
        }
        double confidence = averageConfidence(path);
        if (path.size() > 1) {
            confidence = Math.min(1.0, confidence + 0.05 * (path.size() - 1));
        }
        String predicate = path.isEmpty() ? HeadOntology.LOCATION_TRANSFER : path.get(path.size() - 1).predicate();
        RelationAssertion inferred = new RelationAssertion(subject, predicate, location, confidence);
        candidates.add(buildCandidate(inferred, buildEvidence(path), path.size()));
        if (query != null && query.type() == QueryGoal.Type.WHERE) {
            if (requestedEntity.isPresent() && !requestedEntity.get().equals(subject)) {
                return;
            }
            if (matchesType(graph, ontology, subject, query.entityType())) {
                candidates.add(buildAnswerCandidate(inferred, path));
            }
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
        String answer = assertion.subject() + " " + displayPredicate(assertion.predicate()) + " " + assertion.object();

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

}
