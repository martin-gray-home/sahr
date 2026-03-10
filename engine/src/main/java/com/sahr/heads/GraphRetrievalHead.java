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
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphRetrievalHead extends BaseHead {
    private static final int MAX_LOCATION_DEPTH = 6;

    @Override
    public String getName() {
        return "graph-retrieval";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Retrieves location answers, following short location chains and colocation cues.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.WHERE) {
            return List.of();
        }

        String requestedType = query.entityType();
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        java.util.Optional<SymbolId> requestedEntity = resolveEntityFromQuery(query, graph);

        java.util.Set<String> locationPredicates = HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER);
        if (locationPredicates.isEmpty()) {
            return List.of();
        }
        List<ReasoningCandidate> candidates = new ArrayList<>();
        Map<SymbolId, List<RelationAssertion>> adjacency = buildAdjacency(graph, locationPredicates);
        java.util.Set<String> emitted = new java.util.HashSet<>();
        List<RelationAssertion> locationAssertions = collectLocationAssertions(graph, locationPredicates);
        java.util.Set<String> expandedCoLocation = HeadOntology.expandFamilyWithInverses(ontology, HeadOntology.COLOCATION);
        for (String predicate : locationPredicates) {
            for (RelationAssertion assertion : graph.findByPredicate(predicate)) {
                boolean typeMatch = matchesType(graph, ontology, assertion, requestedType, requestedEntity);
                if (!typeMatch) {
                    continue;
                }
                List<RelationAssertion> path = new ArrayList<>();
                path.add(assertion);
                SymbolId terminal = followLocationChain(assertion.object(), adjacency, path);
                String key = assertion.subject().value() + "|" + terminal.value();
                if (!emitted.add(key)) {
                    continue;
                }

                double queryMatch = 1.0;
                double entityMatch = 1.0;
                double ontologySupport = requestedType == null ? 0.5 : 1.0;
                double graphConfidence = averageConfidence(path);
                double memoryFocus = memory.isActiveEntity(assertion.subject()) ? 1.0 : 0.6;
                double depthBoost = Math.max(0.0, 0.05 * (path.size() - 1));
                double score = normalize(queryMatch, entityMatch, ontologySupport,
                        Math.min(1.0, graphConfidence + depthBoost), memoryFocus);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("query_match", queryMatch);
                breakdown.put("entity_type_match", entityMatch);
                breakdown.put("ontology_support", ontologySupport);
                breakdown.put("graph_confidence", graphConfidence);
                breakdown.put("depth_boost", depthBoost);
                breakdown.put("working_memory_focus", memoryFocus);

                String answer = assertion.subject() + " " + displayPredicate(path.get(path.size() - 1).predicate()) + " " + terminal;

                candidates.add(new ReasoningCandidate(
                        CandidateType.ANSWER,
                        answer,
                        score,
                        getName(),
                        buildEvidence(path),
                        breakdown,
                        path.size()
                ));
            }
        }

        for (RelationAssertion relation : graph.getAllAssertions()) {
            if (!expandedCoLocation.contains(relation.predicate())) {
                continue;
            }
            for (RelationAssertion location : locationAssertions) {
                SymbolId inferredSubject = null;
                if (relation.subject().equals(location.subject())) {
                    inferredSubject = relation.object();
                } else if (relation.object().equals(location.subject())) {
                    inferredSubject = relation.subject();
                }
                if (inferredSubject == null) {
                    continue;
                }
                if (!matchesType(graph, ontology, inferredSubject, requestedType, requestedEntity)) {
                    continue;
                }
                String key = inferredSubject.value() + "|" + location.object().value();
                if (!emitted.add(key)) {
                    continue;
                }

                double queryMatch = 1.0;
                double entityMatch = 1.0;
                double ontologySupport = requestedType == null ? 0.5 : 1.0;
                double graphConfidence = averageConfidence(relation.confidence(), location.confidence());
                double memoryFocus = memory.isActiveEntity(inferredSubject) ? 1.0 : 0.6;
                double score = normalize(queryMatch, entityMatch, ontologySupport, graphConfidence, memoryFocus);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("query_match", queryMatch);
                breakdown.put("entity_type_match", entityMatch);
                breakdown.put("ontology_support", ontologySupport);
                breakdown.put("graph_confidence", graphConfidence);
                breakdown.put("working_memory_focus", memoryFocus);

                String answer = inferredSubject + " " + displayPredicate(location.predicate()) + " " + location.object();
                candidates.add(new ReasoningCandidate(
                        CandidateType.ANSWER,
                        answer,
                        score,
                        getName(),
                        List.of(relation.toString(), location.toString()),
                        breakdown,
                        1
                ));
            }
        }

        return candidates;
    }

    private Map<SymbolId, List<RelationAssertion>> buildAdjacency(KnowledgeBase graph, java.util.Set<String> locationPredicates) {
        Map<SymbolId, List<RelationAssertion>> adjacency = new java.util.HashMap<>();
        for (String predicate : locationPredicates) {
            for (RelationAssertion assertion : graph.findByPredicate(predicate)) {
                adjacency.computeIfAbsent(assertion.subject(), key -> new ArrayList<>()).add(assertion);
            }
        }
        return adjacency;
    }

    private List<RelationAssertion> collectLocationAssertions(KnowledgeBase graph, java.util.Set<String> locationPredicates) {
        List<RelationAssertion> locationAssertions = new ArrayList<>();
        for (String predicate : locationPredicates) {
            locationAssertions.addAll(graph.findByPredicate(predicate));
        }
        return locationAssertions;
    }

    private SymbolId followLocationChain(SymbolId current,
                                         Map<SymbolId, List<RelationAssertion>> adjacency,
                                         List<RelationAssertion> path) {
        java.util.Set<SymbolId> visited = new java.util.HashSet<>();
        visited.add(current);
        SymbolId cursor = current;
        for (int depth = 0; depth < MAX_LOCATION_DEPTH; depth++) {
            List<RelationAssertion> nextEdges = adjacency.getOrDefault(cursor, List.of());
            if (nextEdges.isEmpty()) {
                break;
            }
            RelationAssertion next = nextEdges.get(0);
            if (!visited.add(next.object())) {
                break;
            }
            path.add(next);
            cursor = next.object();
        }
        return cursor;
    }

    private boolean matchesType(KnowledgeBase graph,
                                OntologyService ontology,
                                SymbolId subject,
                                String requestedType,
                                java.util.Optional<SymbolId> requestedEntity) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        if (requestedEntity.isPresent()) {
            return requestedEntity.get().equals(subject);
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

    private boolean matchesType(KnowledgeBase graph,
                                OntologyService ontology,
                                RelationAssertion assertion,
                                String requestedType,
                                java.util.Optional<SymbolId> requestedEntity) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        if (requestedEntity.isPresent()) {
            return requestedEntity.get().equals(assertion.subject());
        }
        String normalizedRequested = normalizeTypeToken(requestedType);
        if (normalizeTypeToken(assertion.subject().value()).equals(normalizedRequested)) {
            return true;
        }
        return graph.findEntity(assertion.subject())
                .map(EntityNode::conceptTypes)
                .map(types -> types.stream().anyMatch(type ->
                        type.equals(requestedType)
                                || normalizeTypeToken(type).equals(normalizedRequested)
                                || ontology.isSubclassOf(type, requestedType)))
                .orElse(false);
    }

}
