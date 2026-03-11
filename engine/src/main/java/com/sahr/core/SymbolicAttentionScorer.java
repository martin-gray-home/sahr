package com.sahr.core;

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

        double entityMatch = matchEntityType(graph, ontology, triple.get().subject, query.entityType());
        double relationMatch = matchLocationRelation(ontology, triple.get().predicate, query.expectedRange());
        double typeMatch = matchRange(ontology, triple.get().predicate, query.expectedRange());

        return QueryMatchResult.of(entityMatch, relationMatch, typeMatch);
    }

    private QueryMatchResult scoreRelation(HeadContext context, ReasoningCandidate candidate, QueryGoal query) {
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();

        double entityMatch = matchRelationEntity(candidate, query);
        double relationMatch = matchRelationPredicate(context, candidate, query);
        double typeMatch = matchExpectedType(graph, ontology, candidate, query.expectedType());

        return QueryMatchResult.of(entityMatch, relationMatch, typeMatch);
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

    private double matchLocationRelation(OntologyService ontology, String predicate, String expectedRange) {
        if (predicate == null) {
            return DEFAULT_RELATION_MATCH;
        }
        if (isLocationPredicate(ontology, predicate)) {
            return 1.0;
        }
        if (expectedRange != null && isIri(predicate)) {
            return matchRange(ontology, predicate, expectedRange) > 0.6 ? 1.0 : 0.6;
        }
        return DEFAULT_RELATION_MATCH;
    }

    private double matchRange(OntologyService ontology, String predicate, String expectedRange) {
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
            if (range.equals(expectedRange) || ontology.isSubclassOf(range, expectedRange)) {
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

    private double matchRelationPredicate(HeadContext context, ReasoningCandidate candidate, QueryGoal query) {
        Optional<Triple> triple = extractTripleFromEvidence(candidate);
        if (triple.isEmpty() || query.predicate() == null) {
            return DEFAULT_RELATION_MATCH;
        }
        String predicate = triple.get().predicate;
        if (predicate.equals(query.predicate())) {
            return 1.0;
        }
        OntologyService ontology = context.ontology();
        if (isIri(query.predicate())) {
            if (ontology.getSubproperties(query.predicate()).contains(predicate)) {
                return 1.0;
            }
            Optional<String> inverse = ontology.getInverseProperty(query.predicate());
            if (inverse.isPresent() && (inverse.get().equals(predicate)
                    || ontology.getSubproperties(inverse.get()).contains(predicate))) {
                return 1.0;
            }
        }
        return DEFAULT_RELATION_MATCH;
    }

    private double matchExpectedType(KnowledgeBase graph, OntologyService ontology, ReasoningCandidate candidate, String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
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
        for (String type : entity.get().conceptTypes()) {
            if (type.equals(expectedType)) {
                return 1.0;
            }
            if (ontology.isSubclassOf(type, expectedType)) {
                return 1.0;
            }
        }
        return 0.2;
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
        private final double queryMatchScore;

        private QueryMatchResult(double entityMatch, double relationMatch, double typeMatch) {
            this.entityMatch = clamp(entityMatch);
            this.relationMatch = clamp(relationMatch);
            this.typeMatch = clamp(typeMatch);
            this.queryMatchScore = clamp(this.entityMatch * this.relationMatch * this.typeMatch);
        }

        public static QueryMatchResult of(double entityMatch, double relationMatch, double typeMatch) {
            return new QueryMatchResult(entityMatch, relationMatch, typeMatch);
        }

        public static QueryMatchResult neutral(double score) {
            return new QueryMatchResult(score, 1.0, 1.0);
        }

        public static QueryMatchResult full() {
            return new QueryMatchResult(1.0, 1.0, 1.0);
        }

        public double queryMatchScore() {
            return queryMatchScore;
        }

        public Map<String, Double> breakdown(double headScore, double finalScore) {
            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("attention_entity_match", entityMatch);
            breakdown.put("attention_relation_match", relationMatch);
            breakdown.put("attention_type_match", typeMatch);
            breakdown.put("attention_query_match", queryMatchScore);
            breakdown.put("attention_head_score", headScore);
            breakdown.put("attention_final_score", finalScore);
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
}
