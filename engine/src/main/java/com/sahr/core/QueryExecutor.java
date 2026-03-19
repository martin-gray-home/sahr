package com.sahr.core;

import com.sahr.ontology.SemanticTypeCompatibilityService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class QueryExecutor {
    private final PredicateResolver predicateResolver;

    public QueryExecutor(PredicateResolver predicateResolver) {
        this.predicateResolver = predicateResolver == null ? new PredicateResolver(java.util.Map.of()) : predicateResolver;
    }

    public QueryResult execute(QueryFrame frame,
                               KnowledgeBase graph,
                               OntologyService ontology,
                               SemanticTypeCompatibilityService compatibility) {
        return execute(frame, graph, graph, ontology, compatibility);
    }

    public QueryResult execute(QueryFrame frame,
                               KnowledgeBase graph,
                               KnowledgeBase focusedGraph,
                               OntologyService ontology,
                               SemanticTypeCompatibilityService compatibility) {
        if (frame == null || graph == null || ontology == null || compatibility == null) {
            return new QueryResult(QueryOperator.RETRIEVE, List.of(), 0L, false, List.of());
        }
        QueryResult focused = executeSingle(frame, focusedGraph == null ? graph : focusedGraph, ontology, compatibility);
        if (!(focusedGraph instanceof FocusedKnowledgeBase focusedView) || !focusedView.isReduced()) {
            return focused;
        }
        QueryResult full = executeSingle(frame, graph, ontology, compatibility);
        return merge(frame.operator(), focused, full);
    }

    private QueryResult executeSingle(QueryFrame frame,
                                      KnowledgeBase graph,
                                      OntologyService ontology,
                                      SemanticTypeCompatibilityService compatibility) {
        if (frame == null || graph == null || ontology == null || compatibility == null) {
            return new QueryResult(QueryOperator.RETRIEVE, List.of(), 0L, false, List.of());
        }
        String predicate = frame.predicate();
        if (predicate == null || predicate.isBlank()) {
            return new QueryResult(frame.operator(), List.of(), 0L, false, List.of());
        }
        SymbolId subject = frame.subject() == null || frame.subject().isBlank() ? null : new SymbolId(frame.subject());
        SymbolId object = frame.object() == null || frame.object().isBlank() ? null : new SymbolId(frame.object());
        String expectedType = frame.typeConstraint();
        String modifier = frame.modifier();
        boolean countMode = frame.operator() == QueryOperator.COUNT;

        if (!modifierSatisfied(graph, ontology, subject, object, modifier)) {
            return new QueryResult(frame.operator(), List.of(), 0L, false, List.of());
        }

        List<QueryBinding> bindings = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        boolean predicateOnly = subject == null && object == null;
        for (PredicateMatch predicateMatch : predicateResolver.expandPredicateMatches(predicate, ontology)) {
            for (RelationAssertion assertion : graph.findByPredicate(predicateMatch.predicate())) {
                boolean subjectMatch = subject == null || predicateMatch.matchesSubject(assertion, subject);
                boolean objectMatch = object == null || predicateMatch.matchesObject(assertion, object);
                if (!subjectMatch || !objectMatch) {
                    continue;
                }
                SymbolId answer = null;
                if (predicateOnly) {
                    answer = selectPredicateOnlyAnswer(graph, ontology, compatibility, assertion, expectedType);
                } else if (subject != null) {
                    answer = predicateMatch.isSwapped() ? assertion.subject() : assertion.object();
                } else if (object != null) {
                    answer = predicateMatch.isSwapped() ? assertion.object() : assertion.subject();
                }
                if (answer == null) {
                    continue;
                }
                boolean typeMatch = countMode
                        ? matchesExpectedTypeForCount(graph, ontology, compatibility, answer, expectedType)
                        : matchesExpectedType(graph, ontology, compatibility, answer, expectedType);
                if (!typeMatch) {
                    continue;
                }
                String evidenceEntry = assertion.toString();
                bindings.add(new QueryBinding(
                        assertion.subject(),
                        assertion.predicate(),
                        assertion.object(),
                        answer,
                        predicateMatch.type(),
                        predicateMatch.policyStrength(),
                        assertion.confidence(),
                        evidenceEntry
                ));
                evidence.add(evidenceEntry);
            }
        }

        boolean exists = !bindings.isEmpty();
        long count = 0L;
        if (frame.operator() == QueryOperator.COUNT) {
            count = bindings.stream()
                    .map(binding -> binding.answer().value())
                    .distinct()
                    .count();
        }
        return new QueryResult(frame.operator(), bindings, count, exists, evidence);
    }

    private QueryResult merge(QueryOperator operator, QueryResult focused, QueryResult full) {
        List<QueryBinding> bindings = new ArrayList<>();
        Set<String> seenBindings = new HashSet<>();
        mergeBindings(bindings, seenBindings, focused.bindings());
        mergeBindings(bindings, seenBindings, full.bindings());

        List<String> evidence = new ArrayList<>();
        LinkedHashSet<String> seenEvidence = new LinkedHashSet<>();
        mergeEvidence(evidence, seenEvidence, focused.evidence());
        mergeEvidence(evidence, seenEvidence, full.evidence());

        boolean exists = !bindings.isEmpty();
        long count = operator == QueryOperator.COUNT
                ? bindings.stream().map(binding -> binding.answer().value()).distinct().count()
                : full.count();
        return new QueryResult(operator, bindings, count, exists, evidence);
    }

    private void mergeBindings(List<QueryBinding> bindings, Set<String> seenBindings, List<QueryBinding> source) {
        for (QueryBinding binding : source) {
            String key = binding.subject().value()
                    + "|" + binding.predicate()
                    + "|" + binding.object().value()
                    + "|" + binding.answer().value();
            if (!seenBindings.add(key)) {
                continue;
            }
            bindings.add(binding);
        }
    }

    private void mergeEvidence(List<String> evidence, Set<String> seenEvidence, List<String> source) {
        for (String item : source) {
            if (seenEvidence.add(item)) {
                evidence.add(item);
            }
        }
    }

    private boolean modifierSatisfied(KnowledgeBase graph,
                                      OntologyService ontology,
                                      SymbolId subject,
                                      SymbolId object,
                                      String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return true;
        }
        if (subject != null && !entityHasAttribute(graph, ontology, subject, modifier)) {
            return false;
        }
        if (object != null && !entityHasAttribute(graph, ontology, object, modifier)) {
            return false;
        }
        return true;
    }

    private boolean entityHasAttribute(KnowledgeBase graph,
                                       OntologyService ontology,
                                       SymbolId entity,
                                       String modifier) {
        if (entity == null || modifier == null || modifier.isBlank()) {
            return true;
        }
        String normalized = modifier.toLowerCase(Locale.ROOT);
        Set<String> attributePredicates = HeadOntology.expandFamily(ontology, HeadOntology.ATTRIBUTE_RELATION);
        if (attributePredicates.isEmpty()) {
            return false;
        }
        for (RelationAssertion assertion : graph.findBySubject(entity)) {
            if (!attributePredicates.contains(assertion.predicate())) {
                continue;
            }
            String value = assertion.object().value().replace("entity:", "").toLowerCase(Locale.ROOT);
            if (normalized.equals(value)) {
                return true;
            }
        }
        return false;
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

    private boolean matchesExpectedTypeForCount(KnowledgeBase graph,
                                               OntologyService ontology,
                                               SemanticTypeCompatibilityService compatibility,
                                               SymbolId candidate,
                                               String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        String normalized = stripPrefix(expectedType).toLowerCase(java.util.Locale.ROOT);
        if (isGenericThing(normalized)) {
            return true;
        }
        if (!isIri(expectedType)) {
            if (isPersonLike(normalized)) {
                if (isPersonLike(stripPrefix(candidate.value()))
                        || hasTypeMatch(graph, candidate, Set.of("person", "people", "man", "woman", "boy", "girl"))) {
                    return true;
                }
                return hasNoDeclaredTypes(graph, candidate);
            }
            if (stripPrefix(candidate.value()).equalsIgnoreCase(normalized)
                    || hasTypeMatch(graph, candidate, Set.of(normalized))) {
                return true;
            }
            return hasNoDeclaredTypes(graph, candidate);
        }
        return matchesExpectedType(graph, ontology, compatibility, candidate, expectedType);
    }

    private boolean matchesExpectedType(KnowledgeBase graph,
                                        OntologyService ontology,
                                        SemanticTypeCompatibilityService compatibility,
                                        SymbolId candidate,
                                        String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        String normalizedExpected = stripPrefix(expectedType).toLowerCase(java.util.Locale.ROOT);
        if (isGenericThing(normalizedExpected)) {
            return true;
        }
        if (isEntityValue(expectedType)) {
            return candidate.value().equals(expectedType);
        }
        Optional<EntityNode> entity = graph.findEntity(candidate);
        if (entity.isEmpty()) {
            return false;
        }
        if (entity.get().conceptTypes().isEmpty()) {
            return true;
        }
        if (!isIri(expectedType)) {
            String normalized = stripPrefix(expectedType).toLowerCase(java.util.Locale.ROOT);
            if (isPersonLike(normalized)) {
                if (isPersonLike(stripPrefix(candidate.value()))
                        || hasTypeMatch(graph, candidate, PERSON_LIKE_TOKENS)
                        || hasExactMatch(graph, ontology, candidate, WORDNET_PERSON_SYNSET)) {
                    return true;
                }
                return hasNoDeclaredTypes(graph, candidate);
            }
            if (hasTypeMatch(graph, candidate, Set.of(normalized))) {
                return true;
            }
            return hasNoDeclaredTypes(graph, candidate);
        }
        boolean hasIriType = entity.get().conceptTypes().stream().anyMatch(this::isIri);
        if (hasIriType) {
            for (String type : entity.get().conceptTypes()) {
                if (!isIri(type)) {
                    continue;
                }
                if (compatibility.isCompatible(type, expectedType)) {
                    return true;
                }
            }
        }
        if (isPersonLikeExpectedType(ontology, expectedType)) {
            return isPersonLike(stripPrefix(candidate.value()))
                    || hasTypeMatch(graph, candidate, PERSON_LIKE_TOKENS)
                    || hasExactMatch(graph, ontology, candidate, WORDNET_PERSON_SYNSET);
        }
        return false;
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

    private boolean hasNoDeclaredTypes(KnowledgeBase graph, SymbolId candidate) {
        Optional<EntityNode> entity = graph.findEntity(candidate);
        return entity.isEmpty() || entity.get().conceptTypes().isEmpty();
    }

    private boolean hasExactMatch(KnowledgeBase graph, OntologyService ontology, SymbolId candidate, String expectedSynset) {
        if (expectedSynset == null || expectedSynset.isBlank()) {
            return false;
        }
        Optional<EntityNode> entity = graph.findEntity(candidate);
        if (entity.isEmpty()) {
            return false;
        }
        for (String type : entity.get().conceptTypes()) {
            if (ontology.getEntitiesWithAnnotation(SKOS_EXACT_MATCH, expectedSynset).contains(type)) {
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

    private boolean isGenericThing(String value) {
        if (value == null) {
            return false;
        }
        return switch (value) {
            case "entity", "entities", "thing", "things", "object", "objects", "item", "items" -> true;
            default -> false;
        };
    }

    private boolean isPersonLikeExpectedType(OntologyService ontology, String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return false;
        }
        Set<String> normalized = new HashSet<>();
        for (String label : ontology.getLabels(expectedType)) {
            if (label != null && !label.isBlank()) {
                normalized.add(label.toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String token : PERSON_LIKE_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEntityValue(String value) {
        return value != null && (value.startsWith("entity:") || value.startsWith("concept:"));
    }

    private boolean isIri(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("http://") || value.startsWith("https://");
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

    private static final Set<String> PERSON_LIKE_TOKENS = Set.of(
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
    private static final String SKOS_EXACT_MATCH = "http://www.w3.org/2004/02/skos/core#exactMatch";
}
