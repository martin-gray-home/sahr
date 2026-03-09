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
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphRetrievalHead implements SymbolicAttentionHead {
    private static final String PREDICATE_LOCATED_IN = "locatedIn";
    private static final List<String> LOCATION_PREDICATES = List.of("at", PREDICATE_LOCATED_IN, "inside", "in");
    private static final int MAX_LOCATION_DEPTH = 6;
    private static final String SAHR_COLOCATION = "https://sahr.ai/ontology/relations#colocation";
    private static final java.util.Set<String> COLOCATION_PREDICATES = java.util.Set.of(
            "wear", "wearing", "hold", "holding", "carry", "carrying",
            "with", "possess", "have", "opposite", "partOf", SAHR_COLOCATION
    );

    @Override
    public String getName() {
        return "graph-retrieval";
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

        List<ReasoningCandidate> candidates = new ArrayList<>();
        Map<SymbolId, List<RelationAssertion>> adjacency = buildAdjacency(graph);
        java.util.Set<String> emitted = new java.util.HashSet<>();
        List<RelationAssertion> locationAssertions = collectLocationAssertions(graph);
        java.util.Set<String> expandedCoLocation = expandCoLocationPredicates(ontology);
        for (String predicate : LOCATION_PREDICATES) {
            for (RelationAssertion assertion : graph.findByPredicate(predicate)) {
                boolean typeMatch = matchesType(graph, ontology, assertion, requestedType);
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

                String answer = assertion.subject() + " " + PREDICATE_LOCATED_IN + " " + terminal;

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
                if (!matchesType(graph, ontology, inferredSubject, requestedType)) {
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

                String answer = inferredSubject + " " + PREDICATE_LOCATED_IN + " " + location.object();
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

    private Map<SymbolId, List<RelationAssertion>> buildAdjacency(KnowledgeBase graph) {
        Map<SymbolId, List<RelationAssertion>> adjacency = new java.util.HashMap<>();
        for (String predicate : LOCATION_PREDICATES) {
            for (RelationAssertion assertion : graph.findByPredicate(predicate)) {
                adjacency.computeIfAbsent(assertion.subject(), key -> new ArrayList<>()).add(assertion);
            }
        }
        return adjacency;
    }

    private List<RelationAssertion> collectLocationAssertions(KnowledgeBase graph) {
        List<RelationAssertion> locationAssertions = new ArrayList<>();
        for (String predicate : LOCATION_PREDICATES) {
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

    private List<String> buildEvidence(List<RelationAssertion> path) {
        List<String> evidence = new ArrayList<>(path.size());
        for (RelationAssertion assertion : path) {
            evidence.add(assertion.toString());
        }
        return evidence;
    }

    private double averageConfidence(List<RelationAssertion> path) {
        if (path.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RelationAssertion assertion : path) {
            total += assertion.confidence();
        }
        return Math.min(1.0, total / path.size());
    }

    private double averageConfidence(double left, double right) {
        return Math.min(1.0, (left + right) / 2.0);
    }

    private java.util.Set<String> expandCoLocationPredicates(OntologyService ontology) {
        java.util.Set<String> expanded = RelationPredicateAliases.withSahrIriAliases(COLOCATION_PREDICATES);
        for (String predicate : COLOCATION_PREDICATES) {
            if (!RelationPredicateAliases.isIri(predicate)) {
                continue;
            }
            expanded.addAll(ontology.getSubproperties(predicate));
        }
        java.util.Set<String> snapshot = new java.util.HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(expanded::add);
        }
        return expanded;
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

    private boolean matchesType(KnowledgeBase graph, OntologyService ontology, RelationAssertion assertion, String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
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

    private double normalize(double... parts) {
        double total = 0.0;
        for (double part : parts) {
            total += part;
        }
        return Math.min(1.0, total / parts.length);
    }
}
