package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
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
import java.util.Optional;
import java.util.Set;

public final class DependencyChainHead implements SymbolicAttentionHead {
    private static final int MAX_CHAIN_DEPTH = 6;

    @Override
    public String getName() {
        return "dependency-chain";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.RELATION) {
            return List.of();
        }
        if (query.subject() == null || query.subject().isBlank()) {
            return List.of();
        }
        if (query.object() != null && !query.object().isBlank()) {
            return List.of();
        }
        if (query.predicate() == null || query.predicate().isBlank()) {
            return List.of();
        }

        KnowledgeBase graph = context.graph();
        SymbolId start = new SymbolId(query.subject());
        List<RelationAssertion> chain = followChain(graph, start, query.predicate());
        if (chain.size() < 2) {
            return List.of();
        }

        RelationAssertion last = chain.get(chain.size() - 1);
        SymbolId answer = last.object();
        double confidence = averageConfidence(chain);
        double depthBoost = Math.max(0.0, 0.1 * (chain.size() - 1));
        double chainBoost = Math.min(1.0, 0.7 + chain.size() * 0.05);
        double score = Math.min(1.0, confidence + depthBoost);

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("graph_confidence", confidence);
        breakdown.put("chain_length", (double) chain.size());
        breakdown.put("chain_boost", chainBoost);
        breakdown.put("depth_boost", depthBoost);

        List<String> evidence = new ArrayList<>();
        for (RelationAssertion assertion : chain) {
            evidence.add(assertion.toString());
        }

        return List.of(new ReasoningCandidate(
                CandidateType.ANSWER,
                answer,
                score,
                getName(),
                evidence,
                breakdown,
                chain.size()
        ));
    }

    private List<RelationAssertion> followChain(KnowledgeBase graph, SymbolId start, String predicate) {
        List<RelationAssertion> chain = new ArrayList<>();
        Set<SymbolId> visited = new HashSet<>();
        SymbolId current = start;
        List<String> predicates = allowedPredicates(predicate);
        for (int depth = 0; depth < MAX_CHAIN_DEPTH; depth++) {
            if (!visited.add(current)) {
                break;
            }
            RelationAssertion assertion = null;
            for (String candidatePredicate : predicates) {
                SymbolId lookup = current;
                Optional<RelationAssertion> next = graph.findByPredicate(candidatePredicate).stream()
                        .filter(edge -> edge.subject().equals(lookup))
                        .max((left, right) -> Double.compare(left.confidence(), right.confidence()));
                if (next.isPresent()) {
                    assertion = next.get();
                    break;
                }
            }
            if (assertion == null) {
                break;
            }
            chain.add(assertion);
            current = assertion.object();
        }
        return chain;
    }

    private List<String> allowedPredicates(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return List.of();
        }
        if ("poweredBy".equals(predicate)) {
            return List.of("poweredBy", "chargedBy");
        }
        return List.of(predicate);
    }

    private double averageConfidence(List<RelationAssertion> chain) {
        if (chain.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RelationAssertion assertion : chain) {
            total += assertion.confidence();
        }
        return Math.min(1.0, total / chain.size());
    }

    private double normalize(double... parts) {
        double total = 0.0;
        for (double part : parts) {
            total += part;
        }
        return Math.min(1.0, total / parts.length);
    }
}
