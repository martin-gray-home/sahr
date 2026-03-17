package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.HeadOntology;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AttributeQueryHead extends BaseHead {
    @Override
    public String getName() {
        return "attribute-query";
    }

    @Override
    protected String describe(HeadContext context) {
        return "Answers attribute questions (e.g., color) using hasAttribute assertions.";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.ATTRIBUTE) {
            return List.of();
        }
        if (query.subject() == null || query.subject().isBlank()) {
            return List.of();
        }
        SymbolId subject = new SymbolId(query.subject());
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        java.util.Set<String> attributePredicates = HeadOntology.expandFamily(ontology, HeadOntology.ATTRIBUTE_RELATION);
        if (attributePredicates.isEmpty()) {
            return List.of();
        }
        boolean inversePolicyApplied = inversePolicyApplied(ontology, attributePredicates);

        List<ReasoningCandidate> candidates = new ArrayList<>();
        addAttributeCandidates(candidates, graph, subject, attributePredicates, ontology, memory, inversePolicyApplied);
        if (candidates.isEmpty() && subject.value().startsWith("entity:")) {
            SymbolId conceptSubject = new SymbolId("concept:" + subject.value().substring("entity:".length()));
            addAttributeCandidates(candidates, graph, conceptSubject, attributePredicates, ontology, memory, inversePolicyApplied);
        }
        return candidates;
    }

    private void addAttributeCandidates(List<ReasoningCandidate> candidates,
                                        KnowledgeBase graph,
                                        SymbolId subject,
                                        java.util.Set<String> attributePredicates,
                                        OntologyService ontology,
                                        WorkingMemory memory,
                                        boolean inversePolicyApplied) {
        for (RelationAssertion assertion : graph.findBySubject(subject)) {
            if (!attributePredicates.contains(assertion.predicate())) {
                continue;
            }
            String objectValue = assertion.object().value().replace("entity:", "");
            String answer = objectValue;
            double memoryFocus = memory != null && memory.isActiveEntity(subject) ? 1.0 : 0.6;
            double score = normalize(assertion.confidence(), memoryFocus);

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("graph_confidence", assertion.confidence());
            breakdown.put("working_memory_focus", memoryFocus);
            annotatePolicyBreakdown(breakdown, ontology, assertion.predicate(), inversePolicyApplied);

            candidates.add(new ReasoningCandidate(
                    CandidateType.ANSWER,
                    answer,
                    score,
                    getName(),
                    List.of(assertion.toString()),
                    breakdown,
                    0
            ));
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

    private double policyScore(InferencePolicyStrength strength) {
        return switch (strength) {
            case HARD -> 1.0;
            case SOFT -> 0.9;
            case RANKING_HINT -> 0.6;
            case DISABLED -> 0.0;
        };
    }
}
