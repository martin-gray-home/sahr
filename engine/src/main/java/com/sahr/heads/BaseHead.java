package com.sahr.heads;

import com.sahr.core.HeadContext;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.SymbolicAttentionHead;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseHead implements SymbolicAttentionHead {
    @Override
    public String explain(HeadContext context) {
        return getName() + ": " + describe(context);
    }

    protected String describe(HeadContext context) {
        return "running head.";
    }

    protected double normalize(Iterable<Double> parts) {
        double total = 0.0;
        int count = 0;
        for (double part : parts) {
            total += part;
            count += 1;
        }
        return count == 0 ? 0.0 : Math.min(1.0, total / count);
    }

    protected double normalize(double... parts) {
        double total = 0.0;
        for (double part : parts) {
            total += part;
        }
        return parts.length == 0 ? 0.0 : Math.min(1.0, total / parts.length);
    }

    protected double averageConfidence(double left, double right) {
        return Math.min(1.0, (left + right) / 2.0);
    }

    protected double averageConfidence(List<RelationAssertion> assertions) {
        if (assertions.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RelationAssertion assertion : assertions) {
            total += assertion.confidence();
        }
        return Math.min(1.0, total / assertions.size());
    }

    protected List<String> buildEvidence(List<RelationAssertion> path) {
        List<String> evidence = new ArrayList<>(path.size());
        for (RelationAssertion assertion : path) {
            evidence.add(assertion.toString());
        }
        return evidence;
    }

    protected boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    protected String normalizeTypeToken(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.startsWith("concept:")) {
            return raw.substring("concept:".length());
        }
        if (raw.startsWith("entity:")) {
            return raw.substring("entity:".length());
        }
        return raw;
    }

    protected Set<String> expandCoLocationPredicates(OntologyService ontology, Set<String> predicates) {
        Set<String> expanded = RelationPredicateAliases.withSahrIriAliases(predicates);
        for (String predicate : predicates) {
            if (!RelationPredicateAliases.isIri(predicate)) {
                continue;
            }
            expanded.addAll(ontology.getSubproperties(predicate));
        }
        addInversePredicates(ontology, expanded);
        return expanded;
    }

    protected void addInversePredicates(OntologyService ontology, Set<String> expanded) {
        Set<String> snapshot = new HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(expanded::add);
        }
    }

    protected java.util.Optional<SymbolId> resolveEntityFromQuery(QueryGoal query, com.sahr.core.KnowledgeBase graph) {
        if (query == null || query.type() != QueryGoal.Type.WHERE) {
            return java.util.Optional.empty();
        }
        String requestedType = query.entityType();
        if (requestedType == null || requestedType.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = normalizeTypeToken(requestedType);
        if (normalized.isBlank()) {
            return java.util.Optional.empty();
        }
        SymbolId candidate = new SymbolId("entity:" + normalized);
        if (graph.findEntity(candidate).isPresent()) {
            return java.util.Optional.of(candidate);
        }
        return java.util.Optional.empty();
    }
}
