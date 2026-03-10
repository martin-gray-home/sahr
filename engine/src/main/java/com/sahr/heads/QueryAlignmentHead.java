package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        String expectedRange = query.expectedRange();
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        List<ReasoningCandidate> candidates = new ArrayList<>();

        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!matchesType(graph, ontology, assertion, requestedType)) {
                continue;
            }
            if (!matchesRange(ontology, assertion, expectedRange)) {
                continue;
            }

            double queryMatch = 0.9;
            double entityMatch = 1.0;
            double ontologySupport = expectedRange == null ? 0.5 : 0.8;
            double graphConfidence = assertion.confidence();
            double score = normalize(queryMatch, entityMatch, ontologySupport, graphConfidence);

            Map<String, Double> breakdown = new HashMap<>();
            breakdown.put("query_match", queryMatch);
            breakdown.put("entity_type_match", entityMatch);
            breakdown.put("ontology_support", ontologySupport);
            breakdown.put("graph_confidence", graphConfidence);

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

    private boolean matchesRange(OntologyService ontology, RelationAssertion assertion, String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return true;
        }
        if (!isIri(assertion.predicate())) {
            return false;
        }
        for (String range : ontology.getObjectPropertyRanges(assertion.predicate())) {
            if (range.equals(expectedRange) || ontology.isSubclassOf(range, expectedRange)) {
                return true;
            }
        }
        return false;
    }

}
