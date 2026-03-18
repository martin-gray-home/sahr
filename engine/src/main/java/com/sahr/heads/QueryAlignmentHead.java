package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.ontology.SemanticNodeNormalizer;
import com.sahr.ontology.SemanticTypeCompatibilityService;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QueryAlignmentHead extends BaseHead {
    @Override
    public String getName() {
        return "query-alignment";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Aligns WHERE queries with ontology ranges to surface matching location assertions.";
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
        String expectedRange = canonicalExpectedRange(context.ontology(), normalizer, query.expectedRange());
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontology);
        List<ReasoningCandidate> candidates = new ArrayList<>();

        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!matchesType(graph, compatibility, assertion, requestedType, canonicalRequestedType)) {
                continue;
            }
        if (!matchesRange(ontology, normalizer, assertion, expectedRange)) {
            continue;
        }

            double queryMatch = 0.9;
            double entityMatch = 1.0;
            double ontologySupport = expectedRange == null ? 0.5 : 0.8;
            PolicySignal policySignal = policySignal(ontology, assertion.predicate());
            if (policySignal != null) {
                ontologySupport *= policySignal.score();
            }
            double graphConfidence = assertion.confidence();
            double score = normalize(queryMatch, entityMatch, ontologySupport, graphConfidence);

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", queryMatch);
            breakdown.put("entity_type_match", entityMatch);
            breakdown.put("ontology_support", ontologySupport);
            breakdown.put("graph_confidence", graphConfidence);
            if (policySignal != null) {
                breakdown.put("policy_strength", policySignal.score());
                breakdown.put("policy_applied", 1.0);
                breakdown.put(policySignal.ruleKey(), 1.0);
            }

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
                    0
            ));
        }

        return candidates;
    }

    private boolean matchesType(KnowledgeBase graph,
                                SemanticTypeCompatibilityService compatibility,
                                RelationAssertion assertion,
                                String requestedType,
                                String canonicalRequestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        return graph.findEntity(assertion.subject())
                .map(EntityNode::conceptTypes)
                .map(types -> types.stream().anyMatch(type ->
                        type.equals(canonicalRequestedType)
                                || (isIri(type) && isIri(canonicalRequestedType)
                                && compatibility.isCompatible(type, canonicalRequestedType))))
                .orElse(false);
    }

    private boolean matchesRange(OntologyService ontology,
                                 SemanticNodeNormalizer normalizer,
                                 RelationAssertion assertion,
                                 String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return true;
        }
        if (!isIri(assertion.predicate())) {
            return false;
        }
        for (String range : ontology.getObjectPropertyRanges(assertion.predicate())) {
            String canonicalRange = canonicalRange(normalizer, range);
            if (canonicalRange == null || canonicalRange.isBlank()) {
                continue;
            }
            if (canonicalRange.equals(expectedRange) || ontology.isSubclassOf(canonicalRange, expectedRange)) {
                return true;
            }
        }
        return false;
    }

    private String canonicalRange(SemanticNodeNormalizer normalizer, String range) {
        if (range == null || range.isBlank()) {
            return range;
        }
        if (normalizer == null) {
            return range;
        }
        return normalizer.canonicalType(range).orElse(range);
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

    private PolicySignal policySignal(OntologyService ontology, String predicate) {
        if (!(ontology instanceof PropertyPolicyProvider provider)) {
            return null;
        }
        PolicySignal best = null;
        best = pickBest(best, policySignalFor(provider.inversePolicy(predicate), "policy_rule_inverse"));
        best = pickBest(best, policySignalFor(provider.symmetricPolicy(predicate), "policy_rule_symmetric"));
        best = pickBest(best, policySignalFor(provider.transitivePolicy(predicate), "policy_rule_transitive"));
        return best;
    }

    private PolicySignal policySignalFor(java.util.Optional<InferencePolicy> policy, String ruleKey) {
        if (policy == null || policy.isEmpty()) {
            return null;
        }
        InferencePolicy value = policy.get();
        if (!value.enabled() || value.strength() == InferencePolicyStrength.DISABLED) {
            return null;
        }
        return new PolicySignal(ruleKey, policyScore(value.strength()));
    }

    private PolicySignal pickBest(PolicySignal current, PolicySignal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.score() > current.score() ? candidate : current;
    }

    private double policyScore(InferencePolicyStrength strength) {
        return switch (strength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
    }

    private record PolicySignal(String ruleKey, double score) {
    }

    private String canonicalExpectedRange(OntologyService ontology,
                                          SemanticNodeNormalizer normalizer,
                                          String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return null;
        }
        if (isIri(expectedRange)) {
            return expectedRange;
        }
        String stripped = normalizeTypeToken(expectedRange).toLowerCase(java.util.Locale.ROOT);
        if (stripped.isBlank()) {
            return null;
        }
        java.util.Set<String> iris = ontology.getEntityIrisByLabel(stripped);
        if (iris.isEmpty()) {
            if (normalizer != null) {
                return normalizer.canonicalType(stripped).orElse(expectedRange);
            }
            return expectedRange;
        }
        String synset = selectWordNetSynset(iris);
        if (synset != null) {
            return synset;
        }
        return iris.stream().sorted().findFirst().orElse(expectedRange);
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
