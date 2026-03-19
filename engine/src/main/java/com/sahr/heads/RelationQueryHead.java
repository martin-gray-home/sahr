package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryFrame;
import com.sahr.core.QueryGoal;
import com.sahr.core.QueryOperator;
import com.sahr.core.QueryBinding;
import com.sahr.core.QueryExecutor;
import com.sahr.core.QueryNormalizer;
import com.sahr.core.QueryResult;
import com.sahr.core.PredicateResolver;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;
import com.sahr.ontology.SemanticTypeCompatibilityService;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RelationQueryHead extends BaseHead {
    private final Map<String, List<String>> predicateAliases;
    private final PredicateResolver predicateResolver;
    private final QueryExecutor queryExecutor;
    private final QueryNormalizer queryNormalizer;

    public RelationQueryHead() {
        this(Map.of());
    }

    public RelationQueryHead(Map<String, List<String>> predicateAliases) {
        this.predicateAliases = predicateAliases == null ? Map.of() : predicateAliases;
        this.predicateResolver = new PredicateResolver(this.predicateAliases);
        this.queryExecutor = new QueryExecutor(predicateResolver);
        this.queryNormalizer = new QueryNormalizer();
    }

    @Override
    public String getName() {
        return "relation-query";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Answers direct relation queries from the knowledge graph.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.RELATION && query.type() != QueryGoal.Type.YESNO
                && query.type() != QueryGoal.Type.COUNT) {
            return List.of();
        }
        if (context.inputFeatures().isPresent()) {
            Set<String> features = context.inputFeatures().get().features();
            if (features.contains("has_why") || features.contains("has_explain")) {
                return List.of();
            }
        }

        String subjectBinding = query.subject();
        String predicate = query.predicate();
        String objectBinding = query.object();
        if (predicate == null || predicate.isBlank()) {
            return List.of();
        }
        boolean predicateOnly = (subjectBinding == null || subjectBinding.isBlank())
                && (objectBinding == null || objectBinding.isBlank());

        OntologyService ontology = context.ontology();
        String expectedType = canonicalExpectedType(ontology, query.expectedType());
        KnowledgeBase graph = context.graph();
        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontology);
        SymbolId subject = subjectBinding == null || subjectBinding.isBlank() ? null : new SymbolId(subjectBinding);
        SymbolId object = objectBinding == null || objectBinding.isBlank() ? null : new SymbolId(objectBinding);

        if (query.type() == QueryGoal.Type.YESNO) {
            QueryFrame frame = queryNormalizer.normalize(query, QueryOperator.EXISTS, expectedType);
            QueryResult result = queryExecutor.execute(frame, graph, ontology, compatibility);
            if (!result.exists() || result.bindings().isEmpty()) {
                return List.of();
            }
            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", 1.0);
            if (!result.bindings().isEmpty()) {
                QueryBinding binding = result.bindings().get(0);
                breakdown.put("graph_confidence", binding.confidence());
                policyStrengthScore(binding.policyStrength()).ifPresent(value -> {
                    breakdown.put("policy_strength", value);
                    breakdown.put("policy_applied", 1.0);
                    addPolicyRuleBreakdown(breakdown, binding.matchType());
                });
            }
            double score = result.bindings().isEmpty()
                    ? normalize(1.0, 0.8)
                    : normalize(1.0, result.bindings().get(0).confidence());
            return List.of(new ReasoningCandidate(
                    CandidateType.ANSWER,
                    result,
                    score,
                    getName(),
                    result.evidence(),
                    breakdown,
                    0
            ));
        }

        if (query.type() == QueryGoal.Type.COUNT) {
            QueryFrame frame = queryNormalizer.normalize(query, QueryOperator.COUNT, expectedType);
            QueryResult result = queryExecutor.execute(frame, graph, ontology, compatibility);
            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", 1.0);
            breakdown.put("count", (double) result.count());
            return List.of(new ReasoningCandidate(
                    CandidateType.ANSWER,
                    result,
                    normalize(1.0, 0.8),
                    getName(),
                    result.evidence(),
                    breakdown,
                    0
            ));
        }

        List<ReasoningCandidate> candidates = new ArrayList<>();
        QueryFrame frame = queryNormalizer.normalize(query, QueryOperator.RETRIEVE, expectedType);
        QueryResult result = queryExecutor.execute(frame, graph, ontology, compatibility);
        for (QueryBinding binding : result.bindings()) {
            SymbolId answer = binding.answer();

            double queryMatch = queryMatchScore(binding.matchType(), binding.policyStrength());
            double typeMatch = expectedType == null ? 0.5 : 1.0;
            double graphConfidence = binding.confidence();
            double score = normalize(queryMatch, typeMatch, graphConfidence);

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", queryMatch);
            breakdown.put("entity_type_match", typeMatch);
            breakdown.put("graph_confidence", graphConfidence);
            policyStrengthScore(binding.policyStrength()).ifPresent(value -> {
                breakdown.put("policy_strength", value);
                breakdown.put("policy_applied", 1.0);
                addPolicyRuleBreakdown(breakdown, binding.matchType());
            });

            candidates.add(new ReasoningCandidate(
                    CandidateType.ANSWER,
                    answer,
                    score,
                    getName(),
                    List.of(binding.evidence()),
                    breakdown,
                    0
            ));
        }

        return candidates;
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
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        if (value.startsWith("concept:")) {
            return value.substring("concept:".length());
        }
        return value;
    }

    private static final java.util.Set<String> PERSON_LIKE_TOKENS = java.util.Set.of(
            "person",
            "people",
            "human",
            "agent",
            "man",
            "woman",
            "boy",
            "girl"
    );

    private static final String WORDNET_PERSON_SYNSET = "https://en-word.net/id/oewn-00007846-n";

    private boolean isPersonLikeExpectedType(OntologyService ontology, String expectedType) {
        if (expectedType == null || expectedType.isBlank() || !isIri(expectedType)) {
            return false;
        }
        if (WORDNET_PERSON_SYNSET.equals(expectedType)) {
            return true;
        }
        for (String label : ontology.getLabels(expectedType)) {
            String normalized = normalizeLabelToToken(label);
            if (PERSON_LIKE_TOKENS.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeLabelToToken(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private double queryMatchScore(com.sahr.core.PredicateMatchType type, InferencePolicyStrength policyStrength) {
        if (policyStrength == null) {
            return type == com.sahr.core.PredicateMatchType.INVERSE ? 0.9 : 1.0;
        }
        return switch (policyStrength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
    }

    private java.util.Optional<Double> policyStrengthScore(InferencePolicyStrength policyStrength) {
        if (policyStrength == null) {
            return java.util.Optional.empty();
        }
        return switch (policyStrength) {
            case HARD -> java.util.Optional.of(1.0);
            case SOFT -> java.util.Optional.of(0.9);
            case RANKING_HINT -> java.util.Optional.of(0.6);
            case DISABLED -> java.util.Optional.of(0.0);
        };
    }

    private void addPolicyRuleBreakdown(Map<String, Double> breakdown, com.sahr.core.PredicateMatchType type) {
        if (type == com.sahr.core.PredicateMatchType.INVERSE) {
            breakdown.put("policy_rule_inverse", 1.0);
        } else if (type == com.sahr.core.PredicateMatchType.SYMMETRIC) {
            breakdown.put("policy_rule_symmetric", 1.0);
        }
    }

}
