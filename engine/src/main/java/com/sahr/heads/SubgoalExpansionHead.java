package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
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
import java.util.Set;

public final class SubgoalExpansionHead implements SymbolicAttentionHead {
    private static final String SAHR_COLOCATION = "https://sahr.ai/ontology/relations#colocation";
    private static final String EXPECTED_RANGE_LOCATION = "concept:location";
    private static final Set<String> DEFAULT_COLOCATION = Set.of(
            "wear", "with", "hold", "carry", "possess", "have", SAHR_COLOCATION
    );

    @Override
    public String getName() {
        return "subgoal-expansion";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        QueryGoal query = context.query();
        if (query.type() != QueryGoal.Type.WHERE) {
            return List.of();
        }
        String requestedType = query.entityType();
        if (requestedType == null || requestedType.isBlank()) {
            return List.of();
        }

        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        Set<String> coLocationPredicates = expandCoLocationPredicates(ontology);
        List<EntityNode> targets = findEntitiesByType(graph, ontology, requestedType);
        if (targets.isEmpty()) {
            return List.of();
        }

        List<ReasoningCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (EntityNode target : targets) {
            SymbolId targetId = target.id();
            for (RelationAssertion assertion : graph.getAllAssertions()) {
                if (!coLocationPredicates.contains(assertion.predicate())) {
                    continue;
                }
                SymbolId subject = null;
                if (assertion.object().equals(targetId)) {
                    subject = assertion.subject();
                } else if (assertion.subject().equals(targetId)) {
                    subject = assertion.object();
                }
                if (subject == null) {
                    continue;
                }
                String subgoalType = resolveEntityType(graph, subject);
                if (subgoalType == null) {
                    continue;
                }
                String key = targetId.value() + "->" + subgoalType;
                if (!seen.add(key)) {
                    continue;
                }
                QueryGoal subgoal = QueryGoal.where(subgoalType, EXPECTED_RANGE_LOCATION)
                        .withParent(query.goalId(), query.depth() + 1);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("graph_confidence", assertion.confidence());
                breakdown.put("ontology_support", 0.6);
                double score = normalize(breakdown.values());

                candidates.add(new ReasoningCandidate(
                        CandidateType.SUBGOAL,
                        subgoal,
                        score,
                        getName(),
                        List.of(assertion.toString()),
                        breakdown,
                        query.depth() + 1
                ));
            }
        }

        return candidates;
    }

    private Set<String> expandCoLocationPredicates(OntologyService ontology) {
        Set<String> expanded = RelationPredicateAliases.withSahrIriAliases(DEFAULT_COLOCATION);
        for (String predicate : DEFAULT_COLOCATION) {
            if (!RelationPredicateAliases.isIri(predicate)) {
                continue;
            }
            expanded.addAll(ontology.getSubproperties(predicate));
        }
        addInversePredicates(ontology, expanded);
        return expanded;
    }

    private void addInversePredicates(OntologyService ontology, Set<String> expanded) {
        Set<String> snapshot = new HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(expanded::add);
        }
    }

    private List<EntityNode> findEntitiesByType(KnowledgeBase graph, OntologyService ontology, String requestedType) {
        List<EntityNode> matches = new ArrayList<>();
        for (EntityNode entity : graph.getAllEntities()) {
            if (matchesType(ontology, entity, requestedType)) {
                matches.add(entity);
            }
        }
        return matches;
    }

    private boolean matchesType(OntologyService ontology, EntityNode entity, String requestedType) {
        for (String type : entity.conceptTypes()) {
            if (type.equals(requestedType) || ontology.isSubclassOf(type, requestedType)) {
                return true;
            }
        }
        return false;
    }

    private String resolveEntityType(KnowledgeBase graph, SymbolId id) {
        return graph.findEntity(id)
                .map(entity -> entity.conceptTypes().stream().findFirst().orElse(null))
                .orElseGet(() -> "concept:" + stripEntityPrefix(id.value()));
    }

    private String stripEntityPrefix(String value) {
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        return value;
    }

    private double normalize(Iterable<Double> parts) {
        double total = 0.0;
        int count = 0;
        for (double part : parts) {
            total += part;
            count += 1;
        }
        return count == 0 ? 0.0 : Math.min(1.0, total / count);
    }
}
