package com.sahr.core;

import com.sahr.ontology.SemanticTypeCompatibilityService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SymbolicAttentionScorer {
    private static final double NEUTRAL_QUERY_MATCH = 0.5;
    private static final double DEFAULT_ENTITY_MATCH = 0.8;
    private static final double DEFAULT_RELATION_MATCH = 0.7;
    private static final double DEFAULT_TYPE_MATCH = 0.8;
    private static final List<String> LOCATION_PREDICATES = List.of("at", "in", "locatedIn");
    private final WorkingMemoryAttentionScorer workingMemoryAttentionScorer = new WorkingMemoryAttentionScorer();

    public QueryMatchResult score(HeadContext context, ReasoningCandidate candidate) {
        QueryGoal query = context.query();
        if (query.type() == QueryGoal.Type.UNKNOWN) {
            return QueryMatchResult.neutral(1.0);
        }
        if (candidate.type() != CandidateType.ANSWER) {
            return QueryMatchResult.neutral(NEUTRAL_QUERY_MATCH);
        }

        switch (query.type()) {
            case WHERE:
                return scoreWhere(context, candidate, query);
            case RELATION:
                return scoreRelation(context, candidate, query);
            case YESNO:
                return QueryMatchResult.full();
            default:
                return QueryMatchResult.neutral(NEUTRAL_QUERY_MATCH);
        }
    }

    private QueryMatchResult scoreWhere(HeadContext context, ReasoningCandidate candidate, QueryGoal query) {
        Optional<Triple> triple = extractTriple(candidate);
        if (triple.isEmpty()) {
            return QueryMatchResult.neutral(NEUTRAL_QUERY_MATCH);
        }

        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();

        String canonicalExpectedRange = canonicalExpectedRange(context, query.expectedRange());
        double entityMatch = matchEntityType(graph, ontology, triple.get().subject, query.entityType());
        double relationMatch = matchLocationRelation(context, ontology, triple.get().predicate, canonicalExpectedRange);
        double typeMatch = matchRange(context, ontology, triple.get().predicate, canonicalExpectedRange);
        WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus = memoryFocus(context, candidate);

        return QueryMatchResult.of(entityMatch, relationMatch, typeMatch, memoryFocus);
    }

    private QueryMatchResult scoreRelation(HeadContext context, ReasoningCandidate candidate, QueryGoal query) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();

        double entityMatch = matchRelationEntity(candidate, query);
        RelationMatch relationMatch = matchRelationPredicate(context, candidate, query);
        double typeMatch = matchExpectedType(graph, ontology, candidate, query.expectedType());
        WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus = memoryFocus(context, candidate);

        return QueryMatchResult.of(entityMatch, relationMatch.score(), typeMatch, memoryFocus, relationMatch.policy());
    }

    private WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus(HeadContext context, ReasoningCandidate candidate) {
        if (candidate.scoreBreakdown().containsKey("working_memory_focus")) {
            return WorkingMemoryAttentionScorer.MemoryFocusResult.neutral();
        }
        return workingMemoryAttentionScorer.score(context, candidate);
    }

    private double matchEntityType(KnowledgeBase graph, OntologyService ontology, String subject, String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return DEFAULT_ENTITY_MATCH;
        }
        Optional<EntityNode> entity = graph.findEntity(new SymbolId(subject));
        if (entity.isEmpty()) {
            return DEFAULT_ENTITY_MATCH;
        }
        for (String type : entity.get().conceptTypes()) {
            if (type.equals(requestedType)) {
                return 1.0;
            }
            if (ontology.isSubclassOf(type, requestedType)) {
                return 1.0;
            }
        }
        return 0.2;
    }

    private double matchLocationRelation(HeadContext context,
                                         OntologyService ontology,
                                         String predicate,
                                         String expectedRange) {
        if (predicate == null) {
            return DEFAULT_RELATION_MATCH;
        }
        if (isLocationPredicate(ontology, predicate)) {
            return 1.0;
        }
        if (expectedRange != null && isIri(predicate)) {
            return matchRange(context, ontology, predicate, expectedRange) > 0.6 ? 1.0 : 0.6;
        }
        return DEFAULT_RELATION_MATCH;
    }

    private double matchRange(HeadContext context,
                              OntologyService ontology,
                              String predicate,
                              String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return DEFAULT_TYPE_MATCH;
        }
        if (isLocationPredicate(ontology, predicate)) {
            return 1.0;
        }
        if (!isIri(predicate)) {
            return 0.2;
        }
        for (String range : ontology.getObjectPropertyRanges(predicate)) {
            String canonicalRange = canonicalRange(context, range);
            if (canonicalRange == null || canonicalRange.isBlank()) {
                continue;
            }
            if (canonicalRange.equals(expectedRange) || ontology.isSubclassOf(canonicalRange, expectedRange)) {
                return 1.0;
            }
        }
        return 0.2;
    }

    private double matchRelationEntity(ReasoningCandidate candidate, QueryGoal query) {
        Optional<Triple> triple = extractTripleFromEvidence(candidate);
        if (triple.isEmpty()) {
            return DEFAULT_ENTITY_MATCH;
        }
        String subject = triple.get().subject;
        String object = triple.get().object;
        if (query.subject() != null && query.subject().equals(subject)) {
            return 1.0;
        }
        if (query.object() != null && query.object().equals(object)) {
            return 1.0;
        }
        return DEFAULT_ENTITY_MATCH;
    }

    private boolean isLocationPredicate(OntologyService ontology, String predicate) {
        if (predicate == null) {
            return false;
        }
        if (LOCATION_PREDICATES.contains(predicate)) {
            return true;
        }
        Set<String> family = new HashSet<>();
        family.addAll(HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER));
        family.addAll(HeadOntology.expandFamily(ontology, HeadOntology.CONTAINMENT));
        family.addAll(HeadOntology.expandFamily(ontology, HeadOntology.SURFACE_CONTACT));
        return family.contains(predicate);
    }

    private RelationMatch matchRelationPredicate(HeadContext context, ReasoningCandidate candidate, QueryGoal query) {
        Optional<Triple> triple = extractTripleFromEvidence(candidate);
        if (triple.isEmpty() || query.predicate() == null) {
            return new RelationMatch(DEFAULT_RELATION_MATCH, null);
        }
        String predicate = triple.get().predicate;
        if (predicate.equals(query.predicate())) {
            return new RelationMatch(1.0, null);
        }
        OntologyService ontology = context.ontology();
        if (isIri(query.predicate())) {
            if (ontology.getSubproperties(query.predicate()).contains(predicate)) {
                return new RelationMatch(1.0, null);
            }
            Optional<String> inverse = inverseProperty(ontology, query.predicate());
            if (inverse.isPresent() && (inverse.get().equals(predicate)
                    || ontology.getSubproperties(inverse.get()).contains(predicate))) {
                PolicySignal policy = policySignalForInverse(ontology, query.predicate());
                if (policy != null) {
                    return new RelationMatch(policy.score(), policy);
                }
                return new RelationMatch(1.0, null);
            }
        }
        return new RelationMatch(DEFAULT_RELATION_MATCH, null);
    }

    private Optional<String> inverseProperty(OntologyService ontology, String predicate) {
        if (ontology instanceof PropertyPolicyProvider provider) {
            return provider.inverseProperty(predicate);
        }
        return Optional.empty();
    }

    private PolicySignal policySignalForInverse(OntologyService ontology, String predicate) {
        if (!(ontology instanceof PropertyPolicyProvider provider)) {
            return null;
        }
        return provider.inversePolicy(predicate)
                .filter(policy -> policy.enabled() && policy.strength() != com.sahr.semantic.model.InferencePolicyStrength.DISABLED)
                .map(policy -> new PolicySignal("policy_rule_inverse", policyScore(policy.strength())))
                .orElse(null);
    }

    private double policyScore(com.sahr.semantic.model.InferencePolicyStrength strength) {
        return switch (strength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
    }

    private double matchExpectedType(KnowledgeBase graph, OntologyService ontology, ReasoningCandidate candidate, String expectedType) {
        String canonicalExpectedType = canonicalExpectedType(ontology, expectedType);
        if (canonicalExpectedType == null || canonicalExpectedType.isBlank()) {
            return DEFAULT_TYPE_MATCH;
        }
        if (!(candidate.payload() instanceof SymbolId)) {
            return DEFAULT_TYPE_MATCH;
        }
        SymbolId answer = (SymbolId) candidate.payload();
        Optional<EntityNode> entity = graph.findEntity(answer);
        if (entity.isEmpty()) {
            return DEFAULT_TYPE_MATCH;
        }
        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontology);
        for (String type : entity.get().conceptTypes()) {
            if (isIri(canonicalExpectedType) && isIri(type) && compatibility.isCompatible(type, canonicalExpectedType)) {
                return 1.0;
            }
            if (type.equals(canonicalExpectedType)) {
                return 1.0;
            }
            if (ontology.isSubclassOf(type, canonicalExpectedType)) {
                return 1.0;
            }
        }
        return 0.2;
    }

    private String canonicalExpectedType(OntologyService ontology, String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return expectedType;
        }
        if (isIri(expectedType)) {
            return expectedType;
        }
        String stripped = stripPrefix(expectedType);
        if (stripped.isBlank()) {
            return expectedType;
        }
        String normalized = normalizeLabelToToken(stripped);
        if ("entity".equals(normalized) || "concept".equals(normalized) || "thing".equals(normalized)) {
            return null;
        }
        Set<String> iris = ontology.getEntityIrisByLabel(normalized);
        if (iris.isEmpty()) {
            return expectedType;
        }
        String synset = selectWordNetSynset(iris);
        if (synset != null) {
            return synset;
        }
        return iris.stream().sorted().findFirst().orElse(expectedType);
    }

    private String canonicalExpectedRange(HeadContext context, String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return expectedRange;
        }
        if (isIri(expectedRange)) {
            return expectedRange;
        }
        return context.semanticNormalizer()
                .flatMap(normalizer -> normalizer.canonicalType(expectedRange))
                .orElse(expectedRange);
    }

    private String canonicalRange(HeadContext context, String range) {
        if (range == null || range.isBlank()) {
            return range;
        }
        if (isIri(range)) {
            return range;
        }
        return context.semanticNormalizer()
                .flatMap(normalizer -> normalizer.canonicalType(range))
                .orElse(range);
    }

    private String selectWordNetSynset(Set<String> iris) {
        for (String iri : iris) {
            if (iri != null && iri.startsWith("https://en-word.net/id/")) {
                return iri;
            }
        }
        return null;
    }

    private String stripPrefix(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("concept:")) {
            return value.substring("concept:".length());
        }
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        return value;
    }

    private String normalizeLabelToToken(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("^_+", "").replaceAll("_+$", "");
    }

    private Optional<Triple> extractTriple(ReasoningCandidate candidate) {
        if (!(candidate.payload() instanceof String)) {
            return Optional.empty();
        }
        return parseTriple((String) candidate.payload());
    }

    private Optional<Triple> extractTripleFromEvidence(ReasoningCandidate candidate) {
        if (candidate.evidence().isEmpty()) {
            return Optional.empty();
        }
        return parseTriple(candidate.evidence().get(0));
    }

    private Optional<Triple> parseTriple(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) {
            return Optional.empty();
        }
        return Optional.of(new Triple(parts[0], parts[1], parts[2]));
    }

    private boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    public static final class QueryMatchResult {
        private final double entityMatch;
        private final double relationMatch;
        private final double typeMatch;
        private final double workingMemoryFocus;
        private final double activeEntityFocus;
        private final double recentAssertionFocus;
        private final double queryMatchScore;
        private final PolicySignal policySignal;

        private QueryMatchResult(double entityMatch,
                                 double relationMatch,
                                 double typeMatch,
                                 WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus,
                                 PolicySignal policySignal) {
            this.entityMatch = clamp(entityMatch);
            this.relationMatch = clamp(relationMatch);
            this.typeMatch = clamp(typeMatch);
            WorkingMemoryAttentionScorer.MemoryFocusResult safeFocus = memoryFocus == null
                    ? WorkingMemoryAttentionScorer.MemoryFocusResult.neutral()
                    : memoryFocus;
            this.workingMemoryFocus = clamp(safeFocus.focus());
            this.activeEntityFocus = clamp(safeFocus.activeEntityFocus());
            this.recentAssertionFocus = clamp(safeFocus.recentAssertionFocus());
            this.queryMatchScore = clamp(this.entityMatch * this.relationMatch * this.typeMatch * this.workingMemoryFocus);
            this.policySignal = policySignal;
        }

        public static QueryMatchResult of(double entityMatch, double relationMatch, double typeMatch) {
            return new QueryMatchResult(
                    entityMatch,
                    relationMatch,
                    typeMatch,
                    WorkingMemoryAttentionScorer.MemoryFocusResult.neutral(),
                    null
            );
        }

        public static QueryMatchResult of(double entityMatch,
                                          double relationMatch,
                                          double typeMatch,
                                          WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus) {
            return new QueryMatchResult(entityMatch, relationMatch, typeMatch, memoryFocus, null);
        }

        public static QueryMatchResult of(double entityMatch,
                                          double relationMatch,
                                          double typeMatch,
                                          WorkingMemoryAttentionScorer.MemoryFocusResult memoryFocus,
                                          PolicySignal policySignal) {
            return new QueryMatchResult(entityMatch, relationMatch, typeMatch, memoryFocus, policySignal);
        }

        public static QueryMatchResult neutral(double score) {
            return new QueryMatchResult(
                    score,
                    1.0,
                    1.0,
                    WorkingMemoryAttentionScorer.MemoryFocusResult.neutral(),
                    null
            );
        }

        public static QueryMatchResult full() {
            return new QueryMatchResult(
                    1.0,
                    1.0,
                    1.0,
                    WorkingMemoryAttentionScorer.MemoryFocusResult.neutral(),
                    null
            );
        }

        public double queryMatchScore() {
            return queryMatchScore;
        }

        public Map<String, Double> breakdown(double headScore, double finalScore) {
            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("attention_entity_match", entityMatch);
            breakdown.put("attention_relation_match", relationMatch);
            breakdown.put("attention_type_match", typeMatch);
            breakdown.put("attention_working_memory_focus", workingMemoryFocus);
            breakdown.put("attention_active_entity_focus", activeEntityFocus);
            breakdown.put("attention_recent_assertion_focus", recentAssertionFocus);
            breakdown.put("attention_query_match", queryMatchScore);
            breakdown.put("attention_head_score", headScore);
            breakdown.put("attention_final_score", finalScore);
            if (policySignal != null) {
                breakdown.put("policy_strength", policySignal.score());
                breakdown.put("policy_applied", 1.0);
                breakdown.put(policySignal.ruleKey(), 1.0);
            }
            return breakdown;
        }

        private static double clamp(double value) {
            if (value < 0.0) {
                return 0.0;
            }
            if (value > 1.0) {
                return 1.0;
            }
            return value;
        }
    }

    private static final class Triple {
        private final String subject;
        private final String predicate;
        private final String object;

        private Triple(String subject, String predicate, String object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }
    }

    private record RelationMatch(double score, PolicySignal policy) {
    }

    private record PolicySignal(String ruleKey, double score) {
    }
}
