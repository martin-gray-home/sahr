package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.HeadOntology;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;

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

        List<ReasoningCandidate> candidates = new ArrayList<>();
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
}
