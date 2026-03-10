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
import com.sahr.core.WorkingMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RelationQueryHead extends BaseHead {
    private final Map<String, List<String>> predicateAliases;

    public RelationQueryHead() {
        this(Map.of());
    }

    public RelationQueryHead(Map<String, List<String>> predicateAliases) {
        this.predicateAliases = predicateAliases == null ? Map.of() : predicateAliases;
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

        String subjectBinding = query.subject();
        String predicate = query.predicate();
        String objectBinding = query.object();
        if ((subjectBinding == null || subjectBinding.isBlank())
                && (objectBinding == null || objectBinding.isBlank())) {
            return List.of();
        }
        if (predicate == null || predicate.isBlank()) {
            return List.of();
        }

        String expectedType = query.expectedType();
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        SymbolId subject = subjectBinding == null || subjectBinding.isBlank() ? null : new SymbolId(subjectBinding);
        SymbolId object = objectBinding == null || objectBinding.isBlank() ? null : new SymbolId(objectBinding);
        String modifier = query.modifier();

        if (modifier != null && !modifier.isBlank()) {
            if (subject != null && !entityHasAttribute(graph, subject, modifier)) {
                return List.of();
            }
            if (object != null && !entityHasAttribute(graph, object, modifier)) {
                return List.of();
            }
        }

        if (query.type() == QueryGoal.Type.YESNO) {
            return evaluateYesNo(query, graph, ontology, subject, object, predicate, expectedType);
        }

        if (query.type() == QueryGoal.Type.COUNT) {
            return evaluateCount(query, graph, ontology, subject, object, predicate, expectedType);
        }

        List<ReasoningCandidate> candidates = new ArrayList<>();
        for (String predicateKey : expandPredicates(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateKey)) {
                boolean forward = subject != null && assertion.subject().equals(subject);
                boolean inverse = subject != null && assertion.object().equals(subject);
                boolean objectMatch = object != null && assertion.object().equals(object);
                if (!forward && !inverse && !objectMatch) {
                    continue;
                }

                SymbolId answer;
                if (forward) {
                    answer = assertion.object();
                } else if (inverse || objectMatch) {
                    answer = assertion.subject();
                } else {
                    continue;
                }
                if (!matchesExpectedType(graph, ontology, answer, expectedType)) {
                    continue;
                }

                double queryMatch = forward ? 1.0 : 0.9;
                double typeMatch = expectedType == null ? 0.5 : 1.0;
                double graphConfidence = assertion.confidence();
                double memoryFocus = memoryFocus(memory, subject, object, answer);
                double score = normalize(queryMatch, typeMatch, graphConfidence, memoryFocus);

                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("query_match", queryMatch);
                breakdown.put("entity_type_match", typeMatch);
                breakdown.put("graph_confidence", graphConfidence);
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
        }

        return candidates;
    }

    private List<ReasoningCandidate> evaluateCount(QueryGoal query,
                                                   KnowledgeBase graph,
                                                   OntologyService ontology,
                                                   SymbolId subject,
                                                   SymbolId object,
                                                   String predicate,
                                                   String expectedType) {
        String modifier = query.modifier();
        if (modifier != null && !modifier.isBlank()) {
            if (object != null && !entityHasAttribute(graph, object, modifier)) {
                return List.of(buildCountAnswer(0, predicate));
            }
            if (object == null && subject != null && !entityHasAttribute(graph, subject, modifier)) {
                return List.of(buildCountAnswer(0, predicate));
            }
        }
        List<SymbolId> matches = new ArrayList<>();
        for (String predicateKey : expandPredicates(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateKey)) {
                if (object != null && !assertion.object().equals(object)) {
                    continue;
                }
                if (subject != null && !assertion.subject().equals(subject)) {
                    continue;
                }
                SymbolId candidate = assertion.subject();
                if (object != null) {
                    candidate = assertion.subject();
                } else if (subject != null) {
                    candidate = assertion.object();
                }
                if (!matchesExpectedTypeForCount(graph, ontology, candidate, expectedType)) {
                    continue;
                }
                matches.add(candidate);
            }
        }
        long count = matches.stream().map(SymbolId::value).distinct().count();
        return List.of(buildCountAnswer(count, predicate));
    }

    private boolean matchesExpectedTypeForCount(KnowledgeBase graph,
                                                OntologyService ontology,
                                                SymbolId candidate,
                                                String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        String normalized = stripPrefix(expectedType).toLowerCase(java.util.Locale.ROOT);
        if (!isIri(expectedType)) {
            if (isPersonLike(normalized)) {
                return isPersonLike(stripPrefix(candidate.value()))
                        || hasTypeMatch(graph, candidate, Set.of("person", "people", "man", "woman", "boy", "girl"));
            }
            return stripPrefix(candidate.value()).equalsIgnoreCase(normalized)
                    || hasTypeMatch(graph, candidate, Set.of(normalized));
        }
        return matchesExpectedType(graph, ontology, candidate, expectedType);
    }

    private boolean hasTypeMatch(KnowledgeBase graph, SymbolId candidate, Set<String> expected) {
        Optional<EntityNode> entity = graph.findEntity(candidate);
        if (entity.isEmpty()) {
            return false;
        }
        for (String type : entity.get().conceptTypes()) {
            String raw = stripPrefix(type);
            if (expected.contains(raw.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPersonLike(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "person", "people", "man", "woman", "boy", "girl" -> true;
            default -> false;
        };
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

    private ReasoningCandidate buildCountAnswer(long count, String predicate) {
        double score = normalize(1.0, 0.8);
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("query_match", 1.0);
        breakdown.put("count", (double) count);
        return new ReasoningCandidate(
                CandidateType.ANSWER,
                String.valueOf(count),
                score,
                getName(),
                List.of("count:" + predicate),
                breakdown,
                0
        );
    }

    private boolean entityHasAttribute(KnowledgeBase graph, SymbolId entity, String modifier) {
        if (entity == null || modifier == null || modifier.isBlank()) {
            return true;
        }
        String normalized = modifier.toLowerCase(java.util.Locale.ROOT);
        for (RelationAssertion assertion : graph.findBySubject(entity)) {
            if (!"hasAttribute".equals(assertion.predicate())) {
                continue;
            }
            String value = assertion.object().value().replace("entity:", "").toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private double memoryFocus(WorkingMemory memory, SymbolId subject, SymbolId object, SymbolId answer) {
        if (memory == null) {
            return 0.6;
        }
        double focus = 0.6;
        if (object != null && memory.isActiveEntity(object)) {
            focus = Math.max(focus, 0.8);
        }
        if (subject != null && memory.isActiveEntity(subject)) {
            focus = Math.max(focus, 0.9);
        }
        if (memory.isActiveEntity(answer)) {
            focus = Math.max(focus, 1.0);
        }
        return focus;
    }

    private boolean matchesExpectedType(KnowledgeBase graph, OntologyService ontology, SymbolId answer, String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        Optional<EntityNode> entity = graph.findEntity(answer);
        if (entity.isEmpty()) {
            return false;
        }
        for (String type : entity.get().conceptTypes()) {
            if (type.equals(expectedType)) {
                return true;
            }
            if (ontology.isSubclassOf(type, expectedType)) {
                return true;
            }
        }
        if (!isIri(expectedType)) {
            return true;
        }
        // If we only have non-IRI concept tags, avoid hard filtering; let attention scoring rank.
        boolean hasIriType = entity.get().conceptTypes().stream().anyMatch(this::isIri);
        return !hasIriType;
    }

    private List<String> expandPredicates(String predicate, OntologyService ontology) {
        List<String> expanded = new ArrayList<>();
        expanded.add(predicate);
        List<String> aliases = predicateAliases.getOrDefault(predicate, List.of());
        expanded.addAll(aliases);
        if (isIri(predicate)) {
            expanded.addAll(ontology.getSubproperties(predicate));
            ontology.getInverseProperty(predicate).ifPresent(inv -> {
                expanded.add(inv);
                expanded.addAll(ontology.getSubproperties(inv));
            });
        }
        return expanded;
    }

    private List<ReasoningCandidate> evaluateYesNo(QueryGoal query,
                                                   KnowledgeBase graph,
                                                   OntologyService ontology,
                                                   SymbolId subject,
                                                   SymbolId object,
                                                   String predicate,
                                                   String expectedType) {
        if (subject == null || object == null) {
            return List.of();
        }
        for (String predicateKey : expandPredicates(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateKey)) {
                if (assertion.subject().equals(subject) && assertion.object().equals(object)) {
                    return List.of(buildYesAnswer(query, assertion));
                }
            }
        }
        return List.of();
    }

    private ReasoningCandidate buildYesAnswer(QueryGoal query, RelationAssertion assertion) {
        String subjectText = query.subjectText() != null ? query.subjectText() : subjectFromAssertion(assertion);
        String objectText = query.objectText() != null ? query.objectText() : objectFromAssertion(assertion);
        String predicateText = query.predicateText() != null ? query.predicateText() : predicateFromAssertion(assertion);
        predicateText = normalizePredicateText(predicateText);

        String answer = "Yes, " + subjectText + " " + predicateText + " " + objectText;

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("query_match", 1.0);
        breakdown.put("graph_confidence", assertion.confidence());
        double score = normalize(1.0, assertion.confidence());

        return new ReasoningCandidate(
                CandidateType.ANSWER,
                answer,
                score,
                getName(),
                List.of(assertion.toString()),
                breakdown,
                0
        );
    }

    private String subjectFromAssertion(RelationAssertion assertion) {
        return assertion.subject().toString();
    }

    private String objectFromAssertion(RelationAssertion assertion) {
        return assertion.object().toString();
    }

    private String predicateFromAssertion(RelationAssertion assertion) {
        return assertion.predicate();
    }

    private String normalizePredicateText(String predicateText) {
        if (predicateText == null || predicateText.isBlank()) {
            return "is";
        }
        if ("on".equals(predicateText) || "under".equals(predicateText)
                || "above".equals(predicateText) || "below".equals(predicateText)) {
            return "is " + predicateText;
        }
        if (predicateText.startsWith("http://") || predicateText.startsWith("https://")) {
            int idx = Math.max(predicateText.lastIndexOf('#'), predicateText.lastIndexOf('/'));
            if (idx >= 0 && idx < predicateText.length() - 1) {
                return predicateText.substring(idx + 1).replace('_', ' ');
            }
            return predicateText;
        }
        return predicateText.replace('_', ' ');
    }

}
