package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.HeadOntology;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.ontology.SemanticTypeCompatibilityService;

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

        String expectedType = query.expectedType();
        KnowledgeBase graph = context.graph();
        OntologyService ontology = context.ontology();
        WorkingMemory memory = context.workingMemory();
        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontology);
        SymbolId subject = subjectBinding == null || subjectBinding.isBlank() ? null : new SymbolId(subjectBinding);
        SymbolId object = objectBinding == null || objectBinding.isBlank() ? null : new SymbolId(objectBinding);
        String modifier = query.modifier();
        if (isDiscourseModifier(query.discourseModifier())) {
            modifier = null;
        }

        if (modifier != null && !modifier.isBlank()) {
            if (subject != null && !entityHasAttribute(graph, ontology, subject, modifier)) {
                return List.of();
            }
            if (object != null && !entityHasAttribute(graph, ontology, object, modifier)) {
                return List.of();
            }
        }

        if (query.type() == QueryGoal.Type.YESNO) {
            return evaluateYesNo(query, graph, ontology, subject, object, predicate, expectedType);
        }

        if (query.type() == QueryGoal.Type.COUNT) {
            return evaluateCount(query, graph, ontology, compatibility, subject, object, predicate, expectedType);
        }

        List<ReasoningCandidate> candidates = new ArrayList<>();
        for (PredicateMatch predicateMatch : expandPredicateMatches(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateMatch.predicate())) {
                boolean subjectMatch = subject == null || predicateMatch.matchesSubject(assertion, subject);
                boolean objectMatch = object == null || predicateMatch.matchesObject(assertion, object);
                SymbolId answer = null;

                if (predicateOnly) {
                    answer = selectPredicateOnlyAnswer(graph, ontology, compatibility, assertion, expectedType);
                    if (answer == null) {
                        continue;
                    }
                } else {
                    if (!subjectMatch || !objectMatch) {
                        continue;
                    }
                    if (subject != null) {
                        answer = predicateMatch.isSwapped() ? assertion.subject() : assertion.object();
                    } else if (object != null) {
                        answer = predicateMatch.isSwapped() ? assertion.object() : assertion.subject();
                    }
                }
                if (answer == null) {
                    continue;
                }
                if (!matchesExpectedType(graph, ontology, compatibility, answer, expectedType)) {
                    continue;
                }

                double queryMatch = predicateMatch.queryMatchScore();
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

        if (candidates.isEmpty() && subject == null && object != null && ontology.isSymmetricProperty(predicate)) {
            candidates.addAll(evaluateSymmetricFallback(graph, ontology, compatibility, memory, object, predicate, expectedType));
        }

        return candidates;
    }

    private SymbolId selectPredicateOnlyAnswer(KnowledgeBase graph,
                                               OntologyService ontology,
                                               SemanticTypeCompatibilityService compatibility,
                                               RelationAssertion assertion,
                                               String expectedType) {
        SymbolId subject = assertion.subject();
        SymbolId object = assertion.object();
        if (expectedType == null || expectedType.isBlank()) {
            return subject;
        }
        if (matchesExpectedType(graph, ontology, compatibility, subject, expectedType)) {
            return subject;
        }
        if (matchesExpectedType(graph, ontology, compatibility, object, expectedType)) {
            return object;
        }
        return null;
    }

    private List<ReasoningCandidate> evaluateCount(QueryGoal query,
                                                   KnowledgeBase graph,
                                                   OntologyService ontology,
                                                   SemanticTypeCompatibilityService compatibility,
                                                   SymbolId subject,
                                                   SymbolId object,
                                                   String predicate,
                                                   String expectedType) {
        String modifier = query.modifier();
        if (isDiscourseModifier(query.discourseModifier())) {
            modifier = null;
        }
        if (modifier != null && !modifier.isBlank()) {
            if (object != null && !entityHasAttribute(graph, ontology, object, modifier)) {
                return List.of(buildCountAnswer(0, predicate));
            }
            if (object == null && subject != null && !entityHasAttribute(graph, ontology, subject, modifier)) {
                return List.of(buildCountAnswer(0, predicate));
            }
        }
        List<SymbolId> matches = new ArrayList<>();
        for (PredicateMatch predicateMatch : expandPredicateMatches(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateMatch.predicate())) {
                boolean subjectMatch = subject == null || predicateMatch.matchesSubject(assertion, subject);
                boolean objectMatch = object == null || predicateMatch.matchesObject(assertion, object);
                if (!subjectMatch || !objectMatch) {
                    continue;
                }
                SymbolId candidate = assertion.subject();
                if (object != null) {
                    candidate = predicateMatch.isSwapped() ? assertion.object() : assertion.subject();
                } else if (subject != null) {
                    candidate = predicateMatch.isSwapped() ? assertion.subject() : assertion.object();
                }
                if (!matchesExpectedTypeForCount(graph, ontology, compatibility, candidate, expectedType)) {
                    continue;
                }
                matches.add(candidate);
            }
        }
        long count = matches.stream().map(SymbolId::value).distinct().count();
        return List.of(buildCountAnswer(count, predicate));
    }

    private List<ReasoningCandidate> evaluateSymmetricFallback(KnowledgeBase graph,
                                                               OntologyService ontology,
                                                               SemanticTypeCompatibilityService compatibility,
                                                               WorkingMemory memory,
                                                               SymbolId anchor,
                                                               String predicate,
                                                               String expectedType) {
        List<ReasoningCandidate> candidates = new ArrayList<>();
        for (PredicateMatch predicateMatch : expandPredicateMatches(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateMatch.predicate())) {
                boolean anchorMatch = predicateMatch.isSwapped()
                        ? assertion.object().equals(anchor)
                        : assertion.subject().equals(anchor);
                if (!anchorMatch) {
                    continue;
                }
                SymbolId answer = predicateMatch.isSwapped() ? assertion.subject() : assertion.object();
                if (!matchesExpectedType(graph, ontology, compatibility, answer, expectedType)) {
                    continue;
                }

                double queryMatch = 0.85;
                double typeMatch = expectedType == null ? 0.5 : 1.0;
                double graphConfidence = assertion.confidence();
                double memoryFocus = memoryFocus(memory, anchor, null, answer);
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

    private boolean matchesExpectedTypeForCount(KnowledgeBase graph,
                                               OntologyService ontology,
                                               SemanticTypeCompatibilityService compatibility,
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
        return matchesExpectedType(graph, ontology, compatibility, candidate, expectedType);
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

    private boolean entityHasAttribute(KnowledgeBase graph, OntologyService ontology, SymbolId entity, String modifier) {
        if (entity == null || modifier == null || modifier.isBlank()) {
            return true;
        }
        String normalized = modifier.toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> attributePredicates = HeadOntology.expandFamily(ontology, HeadOntology.ATTRIBUTE_RELATION);
        if (attributePredicates.isEmpty()) {
            return false;
        }
        for (RelationAssertion assertion : graph.findBySubject(entity)) {
            if (!attributePredicates.contains(assertion.predicate())) {
                continue;
            }
            String value = assertion.object().value().replace("entity:", "").toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDiscourseModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return false;
        }
        String normalized = modifier.toLowerCase(java.util.Locale.ROOT);
        return "else".equals(normalized) || "other".equals(normalized) || "another".equals(normalized);
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

    private boolean matchesExpectedType(KnowledgeBase graph,
                                        OntologyService ontology,
                                        SemanticTypeCompatibilityService compatibility,
                                        SymbolId answer,
                                        String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        Optional<EntityNode> entity = graph.findEntity(answer);
        if (entity.isEmpty()) {
            return false;
        }
        if (!isIri(expectedType)) {
            return true;
        }
        boolean hasIriType = entity.get().conceptTypes().stream().anyMatch(this::isIri);
        if (!hasIriType) {
            return !isPersonLikeExpectedType(ontology, expectedType);
        }
        for (String type : entity.get().conceptTypes()) {
            if (!isIri(type)) {
                continue;
            }
            if (compatibility.isCompatible(type, expectedType)) {
                return true;
            }
        }
        if (isPersonLikeExpectedType(ontology, expectedType)) {
            return false;
        }
        return false;
    }

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

    private List<PredicateMatch> expandPredicateMatches(String predicate, OntologyService ontology) {
        List<PredicateMatch> expanded = new ArrayList<>();
        expanded.add(new PredicateMatch(predicate, MatchType.DIRECT));
        if (ontology.isSymmetricProperty(predicate)) {
            expanded.add(new PredicateMatch(predicate, MatchType.SYMMETRIC));
        }
        List<String> aliases = predicateAliases.getOrDefault(predicate, List.of());
        for (String alias : aliases) {
            expanded.add(new PredicateMatch(alias, MatchType.DIRECT));
        }
        if (isIri(predicate)) {
            for (String subproperty : ontology.getSubproperties(predicate)) {
                expanded.add(new PredicateMatch(subproperty, MatchType.DIRECT));
            }
            ontology.getInverseProperty(predicate).ifPresent(inv -> {
                expanded.add(new PredicateMatch(inv, MatchType.INVERSE));
                for (String subproperty : ontology.getSubproperties(inv)) {
                    expanded.add(new PredicateMatch(subproperty, MatchType.INVERSE));
                }
            });
            return expanded;
        }
        java.util.Set<String> locationFamily = HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER);
        if (locationFamily.contains(predicate)) {
            for (String relation : locationFamily) {
                expanded.add(new PredicateMatch(relation, MatchType.DIRECT));
            }
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
        for (PredicateMatch predicateMatch : expandPredicateMatches(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateMatch.predicate())) {
                boolean subjectMatch = predicateMatch.matchesSubject(assertion, subject);
                boolean objectMatch = predicateMatch.matchesObject(assertion, object);
                if (subjectMatch && objectMatch) {
                    return List.of(buildYesAnswer(query, assertion, predicateMatch.isInverse(), subject, object));
                }
            }
        }
        return List.of();
    }

    private ReasoningCandidate buildYesAnswer(QueryGoal query,
                                              RelationAssertion assertion,
                                              boolean inverseMatch,
                                              SymbolId subject,
                                              SymbolId object) {
        String subjectText = query.subjectText() != null ? query.subjectText() : subjectFromAssertion(assertion);
        String objectText = query.objectText() != null ? query.objectText() : objectFromAssertion(assertion);
        String predicateText = query.predicateText() != null ? query.predicateText() : predicateFromAssertion(assertion);
        if (inverseMatch) {
            subjectText = query.subjectText() != null ? query.subjectText()
                    : subject == null ? subjectText : subject.value();
            objectText = query.objectText() != null ? query.objectText()
                    : object == null ? objectText : object.value();
            predicateText = query.predicateText() != null ? query.predicateText() : query.predicate();
        }
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

    private enum MatchType {
        DIRECT,
        SYMMETRIC,
        INVERSE
    }

    private record PredicateMatch(String predicate, MatchType type) {
        boolean matchesSubject(RelationAssertion assertion, SymbolId subject) {
            return isSwapped() ? assertion.object().equals(subject) : assertion.subject().equals(subject);
        }

        boolean matchesObject(RelationAssertion assertion, SymbolId object) {
            return isSwapped() ? assertion.subject().equals(object) : assertion.object().equals(object);
        }

        boolean isSwapped() {
            return type != MatchType.DIRECT;
        }

        boolean isInverse() {
            return type == MatchType.INVERSE;
        }

        double queryMatchScore() {
            return type == MatchType.INVERSE ? 0.9 : 1.0;
        }
    }

}
