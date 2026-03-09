package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolicAttentionHead;
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphRetrievalHead implements SymbolicAttentionHead {
    private static final List<String> LOCATION_PREDICATES = List.of("at", "locatedIn");

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
        for (String predicate : LOCATION_PREDICATES) {
            for (RelationAssertion assertion : graph.findByPredicate(predicate)) {
                boolean typeMatch = matchesType(graph, ontology, assertion, requestedType);
                if (!typeMatch) {
                    continue;
                }

                double queryMatch = 1.0;
                double entityMatch = 1.0;
                double ontologySupport = requestedType == null ? 0.5 : 1.0;
                double graphConfidence = assertion.confidence();
                double memoryFocus = memory.isActiveEntity(assertion.subject()) ? 1.0 : 0.6;
                double score = normalize(queryMatch, entityMatch, ontologySupport, graphConfidence, memoryFocus);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("query_match", queryMatch);
                breakdown.put("entity_type_match", entityMatch);
                breakdown.put("ontology_support", ontologySupport);
                breakdown.put("graph_confidence", graphConfidence);
                breakdown.put("working_memory_focus", memoryFocus);

                String answer = assertion.subject() + " " + assertion.predicate() + " " + assertion.object();

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

        return candidates;
    }

    private boolean matchesType(KnowledgeBase graph, OntologyService ontology, RelationAssertion assertion, String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return true;
        }
        return graph.findEntity(assertion.subject())
                .map(EntityNode::conceptTypes)
                .map(types -> types.stream().anyMatch(type -> type.equals(requestedType) || ontology.isSubclassOf(type, requestedType)))
                .orElse(false);
    }

    private double normalize(double... parts) {
        double total = 0.0;
        for (double part : parts) {
            total += part;
        }
        return Math.min(1.0, total / parts.length);
    }
}
