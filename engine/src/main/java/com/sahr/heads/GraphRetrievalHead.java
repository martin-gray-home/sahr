package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadOntology;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.ontology.SemanticNodeNormalizer;
import com.sahr.ontology.SemanticTypeCompatibilityService;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GraphRetrievalHead extends BaseHead {
    private static final int DEFAULT_MAX_LOCATION_DEPTH = 6;
    private static final boolean DEFAULT_ALLOW_COLOCATION = false;
    private final int maxLocationDepth;
    private final boolean allowColocation;

    public GraphRetrievalHead() {
        this(DEFAULT_MAX_LOCATION_DEPTH, DEFAULT_ALLOW_COLOCATION);
    }

    public GraphRetrievalHead(int maxLocationDepth) {
        this(maxLocationDepth, DEFAULT_ALLOW_COLOCATION);
    }

    public GraphRetrievalHead(int maxLocationDepth, boolean allowColocation) {
        this.maxLocationDepth = Math.max(1, maxLocationDepth);
        this.allowColocation = allowColocation;
    }

    @Override
    public String getName() {
        return "graph-retrieval";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Retrieves location answers, following short location chains.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.WHERE) {
            return List.of();
        }

        String requestedType = query.entityType();
        SemanticNodeNormalizer normalizer = context.semanticNormalizer().orElse(null);
        String canonicalRequestedType = canonicalRequestedType(context.ontology(), normalizer, requestedType);
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontology);
        WorkingMemory memory = context.workingMemory();
        java.util.Optional<SymbolId> requestedEntity = resolveEntityFromQuery(query, graph);

        java.util.Set<String> surfacePredicates = HeadOntology.expandFamilyWithInversesTransitive(
                ontology, HeadOntology.SURFACE_CONTACT);
        java.util.Set<String> containmentPredicates = HeadOntology.expandFamilyWithInversesTransitive(
                ontology, HeadOntology.CONTAINMENT);
        java.util.Set<String> locationPredicates = HeadOntology.expandFamilyWithInversesTransitive(
                ontology, HeadOntology.LOCATION_TRANSFER);
        boolean inversePolicyApplied = inversePolicyApplied(ontology, surfacePredicates)
                || inversePolicyApplied(ontology, containmentPredicates)
                || inversePolicyApplied(ontology, locationPredicates);
        java.util.Set<String> directPredicates = new java.util.HashSet<>();
        directPredicates.addAll(surfacePredicates);
        directPredicates.addAll(containmentPredicates);
        directPredicates.addAll(locationPredicates);
        if (locationPredicates.isEmpty() && directPredicates.isEmpty()) {
            return List.of();
        }
        List<ReasoningCandidate> candidates = new ArrayList<>();
        List<Map<String, Double>> breakdowns = new ArrayList<>();
        Map<SymbolId, List<RelationAssertion>> adjacency = buildAdjacency(graph, locationPredicates);
        java.util.Set<String> emitted = new java.util.HashSet<>();
        List<RelationAssertion> locationAssertions = collectLocationAssertions(graph, locationPredicates);
        java.util.Set<String> expandedCoLocation = allowColocation
                ? HeadOntology.expandFamilyWithInverses(ontology, HeadOntology.COLOCATION)
                : java.util.Set.of();
        int suppressedChain = 0;
        int suppressedColocation = 0;

        List<RelationAssertion> directAssertions = collectDirectAssertions(graph, directPredicates);
        java.util.Set<SymbolId> directSubjects = new java.util.HashSet<>();
        for (RelationAssertion assertion : directAssertions) {
            if (!matchesType(graph, ontology, compatibility, assertion,
                    requestedType, canonicalRequestedType, requestedEntity)) {
                continue;
            }
            directSubjects.add(assertion.subject());
            String key = assertion.subject().value() + "|" + assertion.predicate() + "|" + assertion.object().value();
            if (!emitted.add(key)) {
                continue;
            }

            double queryMatch = 1.0;
            double entityMatch = 1.0;
            double ontologySupport = requestedType == null ? 0.5 : 1.0;
            double familyPreference = familyPreference(assertion.predicate(), surfacePredicates,
                    containmentPredicates, locationPredicates);
            double graphConfidence = assertion.confidence();
            double memoryFocus = memory.isActiveEntity(assertion.subject()) ? 1.0 : 0.7;
            double score = normalize(queryMatch, entityMatch, ontologySupport,
                    graphConfidence, memoryFocus, familyPreference);

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", queryMatch);
            breakdown.put("entity_type_match", entityMatch);
            breakdown.put("ontology_support", ontologySupport);
            breakdown.put("graph_confidence", graphConfidence);
            breakdown.put("working_memory_focus", memoryFocus);
            breakdown.put("where_family_preference", familyPreference);
            breakdown.put("where_path_direct", 1.0);
            breakdown.put("where_chain_length", 1.0);
            annotateFamilyBreakdown(breakdown, assertion.predicate(), surfacePredicates,
                    containmentPredicates, locationPredicates, expandedCoLocation);
            annotatePolicyBreakdown(breakdown, ontology, assertion.predicate(), inversePolicyApplied);

            candidates.add(new ReasoningCandidate(
                    CandidateType.ANSWER,
                    new com.sahr.core.QueryResult(
                            com.sahr.core.QueryOperator.RETRIEVE,
                            List.of(),
                            0L,
                            true,
                            List.of(assertion.toString()),
                            List.of(assertion)
                    ),
                    score,
                    getName(),
                    List.of(assertion.toString()),
                    breakdown,
                    1
            ));
            breakdowns.add(breakdown);
        }

        for (RelationAssertion assertion : locationAssertions) {
                boolean typeMatch = matchesType(graph, ontology, compatibility, assertion,
                        requestedType, canonicalRequestedType, requestedEntity);
                if (!typeMatch) {
                    continue;
                }
                if (directSubjects.contains(assertion.subject())) {
                    suppressedChain++;
                    continue;
                }
                List<RelationAssertion> path = new ArrayList<>();
                path.add(assertion);
                SymbolId terminal = followLocationChain(assertion.object(), adjacency, path);
                String key = assertion.subject().value() + "|chain|" + terminal.value();
                if (!emitted.add(key)) {
                    continue;
                }

                double queryMatch = 1.0;
                double entityMatch = 1.0;
                double ontologySupport = requestedType == null ? 0.5 : 1.0;
                double graphConfidence = averageConfidence(path);
                double memoryFocus = memory.isActiveEntity(assertion.subject()) ? 1.0 : 0.6;
                double depthPenalty = Math.max(0.0, 0.05 * (path.size() - 1));
                double depthAdjusted = Math.max(0.0, graphConfidence - depthPenalty);
                double familyPreference = familyPreference(path.get(path.size() - 1).predicate(),
                        surfacePredicates, containmentPredicates, locationPredicates);
                double score = normalize(queryMatch, entityMatch, ontologySupport,
                        depthAdjusted, memoryFocus, familyPreference);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("query_match", queryMatch);
                breakdown.put("entity_type_match", entityMatch);
                breakdown.put("ontology_support", ontologySupport);
                breakdown.put("graph_confidence", graphConfidence);
                breakdown.put("depth_penalty", depthPenalty);
                breakdown.put("working_memory_focus", memoryFocus);
                breakdown.put("where_family_preference", familyPreference);
                breakdown.put("where_path_chain", 1.0);
                breakdown.put("where_chain_length", (double) path.size());
                annotateFamilyBreakdown(breakdown, path.get(path.size() - 1).predicate(), surfacePredicates,
                        containmentPredicates, locationPredicates, expandedCoLocation);
                annotatePolicyBreakdown(breakdown, ontology, path.get(path.size() - 1).predicate(), inversePolicyApplied);

                RelationAssertion inferred = new RelationAssertion(
                        assertion.subject(),
                        path.get(path.size() - 1).predicate(),
                        terminal,
                        depthAdjusted
                );

                candidates.add(new ReasoningCandidate(
                        CandidateType.ANSWER,
                        new com.sahr.core.QueryResult(
                                com.sahr.core.QueryOperator.RETRIEVE,
                                List.of(),
                                0L,
                                true,
                                buildEvidence(path),
                                List.of(inferred)
                        ),
                        score,
                        getName(),
                        buildEvidence(path),
                        breakdown,
                        path.size()
                ));
                breakdowns.add(breakdown);
        }

        if (allowColocation) {
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
                    if (directSubjects.contains(inferredSubject)) {
                        suppressedColocation++;
                        continue;
                    }
                    if (!matchesType(graph, ontology, compatibility, inferredSubject,
                            requestedType, canonicalRequestedType, requestedEntity)) {
                        continue;
                    }
                    String key = inferredSubject.value() + "|colocated|" + location.object().value();
                    if (!emitted.add(key)) {
                        continue;
                    }

                    double queryMatch = 1.0;
                    double entityMatch = 1.0;
                    double ontologySupport = requestedType == null ? 0.5 : 1.0;
                    double graphConfidence = averageConfidence(relation.confidence(), location.confidence());
                    double memoryFocus = memory.isActiveEntity(inferredSubject) ? 1.0 : 0.6;
                    double colocationPenalty = 0.1;
                    double familyPreference = familyPreference(location.predicate(), surfacePredicates,
                            containmentPredicates, locationPredicates);
                    double score = normalize(queryMatch, entityMatch, ontologySupport,
                            Math.max(0.0, graphConfidence - colocationPenalty), memoryFocus, familyPreference);

                    Map<String, Double> breakdown = new HashMap<>();
                    breakdown.put("query_match", queryMatch);
                    breakdown.put("entity_type_match", entityMatch);
                    breakdown.put("ontology_support", ontologySupport);
                    breakdown.put("graph_confidence", graphConfidence);
                    breakdown.put("colocation_penalty", colocationPenalty);
                    breakdown.put("working_memory_focus", memoryFocus);
                    breakdown.put("where_family_preference", familyPreference);
                    breakdown.put("where_path_colocation", 1.0);
                    breakdown.put("where_chain_length", 1.0);
                    annotateFamilyBreakdown(breakdown, location.predicate(), surfacePredicates,
                            containmentPredicates, locationPredicates, expandedCoLocation);
                    annotatePolicyBreakdown(breakdown, ontology, location.predicate(), inversePolicyApplied);

                    RelationAssertion inferred = new RelationAssertion(
                            inferredSubject,
                            location.predicate(),
                            location.object(),
                            Math.max(0.0, graphConfidence - colocationPenalty)
                    );
                    candidates.add(new ReasoningCandidate(
                            CandidateType.ANSWER,
                            new com.sahr.core.QueryResult(
                                    com.sahr.core.QueryOperator.RETRIEVE,
                                    List.of(),
                                    0L,
                                    true,
                                    List.of(relation.toString(), location.toString()),
                                    List.of(inferred)
                            ),
                            score,
                            getName(),
                            List.of(relation.toString(), location.toString()),
                            breakdown,
                            1
                    ));
                    breakdowns.add(breakdown);
                }
            }
        }

        if (suppressedChain > 0 || suppressedColocation > 0) {
            for (Map<String, Double> breakdown : breakdowns) {
                if (suppressedChain > 0) {
                    breakdown.putIfAbsent("where_suppressed_chain", (double) suppressedChain);
                }
                if (suppressedColocation > 0) {
                    breakdown.putIfAbsent("where_suppressed_colocation", (double) suppressedColocation);
                }
            }
        }

        return candidates;
    }

    private List<RelationAssertion> collectDirectAssertions(KnowledgeBase graph, java.util.Set<String> predicates) {
        List<RelationAssertion> assertions = new ArrayList<>();
        for (String predicate : predicates) {
            assertions.addAll(graph.findByPredicate(predicate));
        }
        return assertions;
    }

    private void annotateFamilyBreakdown(Map<String, Double> breakdown,
                                         String predicate,
                                         java.util.Set<String> surfacePredicates,
                                         java.util.Set<String> containmentPredicates,
                                         java.util.Set<String> locationPredicates,
                                         java.util.Set<String> coLocationPredicates) {
        if (predicate == null) {
            return;
        }
        if (surfacePredicates.contains(predicate)) {
            breakdown.put("where_family_surface", 1.0);
        }
        if (containmentPredicates.contains(predicate)) {
            breakdown.put("where_family_containment", 1.0);
        }
        if (locationPredicates.contains(predicate)) {
            breakdown.put("where_family_location", 1.0);
        }
        if (coLocationPredicates.contains(predicate)) {
            breakdown.put("where_family_colocation", 1.0);
        }
    }

    private void annotatePolicyBreakdown(Map<String, Double> breakdown,
                                         OntologyService ontology,
                                         String predicate,
                                         boolean inversePolicyApplied) {
        if (!(ontology instanceof PropertyPolicyProvider provider)) {
            return;
        }
        if (inversePolicyApplied) {
            InferencePolicyStrength strength = provider.inversePolicy(predicate)
                    .map(com.sahr.semantic.model.InferencePolicy::strength)
                    .orElse(null);
            if (strength != null) {
                breakdown.put("policy_strength", policyScore(strength));
                breakdown.put("policy_applied", 1.0);
                breakdown.put("policy_rule_inverse", 1.0);
            }
        }
    }

    private boolean inversePolicyApplied(OntologyService ontology, java.util.Set<String> predicates) {
        if (!(ontology instanceof PropertyPolicyProvider provider)) {
            return false;
        }
        for (String predicate : predicates) {
            if (provider.inversePolicy(predicate).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private double familyPreference(String predicate,
                                    java.util.Set<String> surfacePredicates,
                                    java.util.Set<String> containmentPredicates,
                                    java.util.Set<String> locationPredicates) {
        if (predicate == null) {
            return 0.5;
        }
        if (containmentPredicates.contains(predicate)) {
            return 1.0;
        }
        if (locationPredicates.contains(predicate)) {
            return 0.9;
        }
        if (surfacePredicates.contains(predicate)) {
            return 0.7;
        }
        return 0.5;
    }

    private double policyScore(InferencePolicyStrength strength) {
        return switch (strength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
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
        for (int depth = 0; depth < maxLocationDepth; depth++) {
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
                                SemanticTypeCompatibilityService compatibility,
                                SymbolId subject,
                                String requestedType,
                                String canonicalRequestedType,
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
                        type.equals(canonicalRequestedType)
                                || normalizeTypeToken(type).equals(normalizedRequested)
                                || (isIri(type) && isIri(canonicalRequestedType)
                                && compatibility.isCompatible(type, canonicalRequestedType))))
                .orElse(false);
    }

    private boolean matchesType(KnowledgeBase graph,
                                OntologyService ontology,
                                SemanticTypeCompatibilityService compatibility,
                                RelationAssertion assertion,
                                String requestedType,
                                String canonicalRequestedType,
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
                        type.equals(canonicalRequestedType)
                                || normalizeTypeToken(type).equals(normalizedRequested)
                                || (isIri(type) && isIri(canonicalRequestedType)
                                && compatibility.isCompatible(type, canonicalRequestedType))))
                .orElse(false);
    }

    private String canonicalRequestedType(OntologyService ontology,
                                          SemanticNodeNormalizer normalizer,
                                          String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return null;
        }
        if (isIri(requestedType)) {
            return requestedType;
        }
        String stripped = normalizeTypeToken(requestedType).toLowerCase(java.util.Locale.ROOT);
        if (stripped.isBlank()) {
            return null;
        }
        if ("entity".equals(stripped) || "concept".equals(stripped) || "thing".equals(stripped)) {
            return null;
        }
        java.util.Set<String> iris = ontology.getEntityIrisByLabel(stripped);
        if (iris.isEmpty()) {
            if (normalizer != null) {
                return normalizer.canonicalType(stripped).orElse(requestedType);
            }
            return requestedType;
        }
        String synset = selectWordNetSynset(iris);
        if (synset != null) {
            return synset;
        }
        return iris.stream().sorted().findFirst().orElse(requestedType);
    }

    private String selectWordNetSynset(java.util.Set<String> iris) {
        for (String iri : iris) {
            if (iri != null && iri.startsWith("https://en-word.net/id/")) {
                return iri;
            }
        }
        return null;
    }

}
