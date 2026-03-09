package com.sahr.nlp;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import com.sahr.core.SymbolId;

import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class StatementParser {
    private static final String PREDICATE_AT = "at";
    private static final String PREDICATE_IN = "locatedIn";
    private static final String PREDICATE_TYPE = "rdf:type";
    private static final Set<String> PREPOSITION_PREDICATES = Set.of("inside", "on", "under", "with", "opposite");

    private static final StanfordCoreNLP PIPELINE = buildPipeline();

    public Optional<Statement> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.contains(" is inside ")) {
            return parseBinary(normalized, "is inside", "inside", false);
        }
        if (normalized.contains(" is in ")) {
            return parseBinary(normalized, "is in", PREDICATE_IN, false);
        }
        if (normalized.contains(" is at ")) {
            return parseBinary(normalized, "is at", PREDICATE_AT, false);
        }
        if (normalized.contains(" is with ")) {
            return parseBinary(normalized, "is with", "with", false);
        }
        if (normalized.contains(" is holding ")) {
            return parseBinary(normalized, "is holding", "hold", false);
        }
        if (normalized.contains(" is carrying ")) {
            return parseBinary(normalized, "is carrying", "carry", false);
        }
        if (normalized.contains(" is wearing ")) {
            return parseBinary(normalized, "is wearing", "wear", false);
        }
        Optional<Statement> passive = parsePassiveBy(normalized);
        if (passive.isPresent()) {
            return passive;
        }

        Optional<Statement> coreNlp = parseWithCoreNlp(trimmed);
        if (coreNlp.isPresent()) {
            return coreNlp;
        }
        if (normalized.contains(" is a ") || normalized.contains(" is an ")) {
            return parseBinary(normalized, normalized.contains(" is a ") ? "is a" : "is an", PREDICATE_TYPE, true);
        }

        return Optional.empty();
    }

    private Optional<Statement> parsePassiveBy(String normalized) {
        int verbIndex = normalized.indexOf(" is ");
        int byIndex = normalized.indexOf(" by ");
        if (verbIndex < 0 || byIndex < 0 || byIndex <= verbIndex + 4) {
            return Optional.empty();
        }
        String subjectPart = normalized.substring(0, verbIndex).trim();
        String verbPart = normalized.substring(verbIndex + 4, byIndex).trim();
        String objectPart = normalized.substring(byIndex + 4).trim();
        if (subjectPart.isBlank() || verbPart.isBlank() || objectPart.isBlank()) {
            return Optional.empty();
        }
        String subjectToken = normalizeToken(subjectPart);
        String objectToken = normalizeToken(objectPart);
        if (subjectToken.isEmpty() || objectToken.isEmpty()) {
            return Optional.empty();
        }
        String predicate = verbPart.replaceAll("\\s+", "_") + "By";
        return Optional.of(buildStatement(subjectToken, objectToken, predicate, false));
    }

    private Optional<Statement> parseWithCoreNlp(String input) {
        Annotation doc = new Annotation(input);
        PIPELINE.annotate(doc);
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            if (graph == null) {
                continue;
            }

            Optional<Statement> copula = parseCopular(graph);
            if (copula.isPresent()) {
                return copula;
            }

            Optional<Statement> verbObject = parseVerbObject(graph);
            if (verbObject.isPresent()) {
                return verbObject;
            }

            Optional<Statement> nmod = parseNmod(graph);
            if (nmod.isPresent()) {
                return nmod;
            }
        }
        return Optional.empty();
    }

    private Optional<Statement> parseCopular(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"cop".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel predicate = edge.getGovernor().backingLabel();
            CoreLabel subject = findDependent(graph, edge.getGovernor(), "nsubj");
            if (subject == null) {
                continue;
            }

            String preposition = findCase(graph, edge.getGovernor());
            PrepMatch prepMatch = preposition == null ? findCopularPreposition(graph, edge.getGovernor()) : null;
            if (preposition == null && prepMatch != null) {
                preposition = prepMatch.preposition();
            }
            if (preposition == null && prepMatch == null && isPrepositionPredicate(predicate.word())) {
                return Optional.empty();
            }
            String predicateType = preposition != null ? mapPreposition(preposition, predicate.word()) : PREDICATE_TYPE;
            boolean objectIsConcept = PREDICATE_TYPE.equals(predicateType);

            String subjectToken = normalizeToken(subject.word());
            CoreLabel prepObject = preposition == null ? null : findPrepositionalObject(graph, edge.getGovernor(), preposition);
            if (prepObject == null && prepMatch != null) {
                prepObject = prepMatch.object();
            }
            String objectToken = normalizeToken(prepObject != null ? prepObject.word() : predicate.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            if (preposition == null && hasDeterminer(graph, edge.getGovernor())) {
                return Optional.empty();
            }

            return Optional.of(buildStatement(subjectToken, objectToken, predicateType, objectIsConcept));
        }
        return Optional.empty();
    }

    private boolean isPrepositionPredicate(String word) {
        if (word == null) {
            return false;
        }
        return PREPOSITION_PREDICATES.contains(word.toLowerCase(Locale.ROOT));
    }

    private PrepMatch findCopularPreposition(SemanticGraph graph,
                                             edu.stanford.nlp.ling.IndexedWord governor) {
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null || specific.isBlank()) {
                continue;
            }
            return new PrepMatch(specific.toLowerCase(Locale.ROOT), edge.getDependent().backingLabel());
        }
        return null;
    }

    private static final class PrepMatch {
        private final String preposition;
        private final CoreLabel object;

        private PrepMatch(String preposition, CoreLabel object) {
            this.preposition = preposition;
            this.object = object;
        }

        private String preposition() {
            return preposition;
        }

        private CoreLabel object() {
            return object;
        }
    }

    private CoreLabel findPrepositionalObject(SemanticGraph graph,
                                              edu.stanford.nlp.ling.IndexedWord governor,
                                              String preposition) {
        if (preposition == null) {
            return null;
        }
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null) {
                continue;
            }
            if (!preposition.equalsIgnoreCase(specific)) {
                continue;
            }
            return edge.getDependent().backingLabel();
        }
        return null;
    }

    private Optional<Statement> parseVerbObject(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"obj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel verb = edge.getGovernor().backingLabel();
            CoreLabel object = edge.getDependent().backingLabel();
            CoreLabel subject = findDependent(graph, edge.getGovernor(), "nsubj");
            if (subject == null) {
                continue;
            }

            String subjectToken = normalizeToken(subject.word());
            String objectToken = normalizeToken(object.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }

            String predicate = verb.lemma().toLowerCase(Locale.ROOT);
            return Optional.of(buildStatement(subjectToken, objectToken, predicate, false));
        }
        return Optional.empty();
    }

    private Optional<Statement> parseNmod(SemanticGraph graph) {
        java.util.Map<String, Statement> statements = new java.util.LinkedHashMap<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            CoreLabel subjectLabel = findDependent(graph, edge.getGovernor(), "nsubj");
            CoreLabel head = edge.getGovernor().backingLabel();
            CoreLabel object = edge.getDependent().backingLabel();
            String specific = edge.getRelation().getSpecific();
            String predicate = specific != null ? mapPreposition(specific.toLowerCase(Locale.ROOT), head.word()) : "nmod";

            String subjectToken = normalizeToken(subjectLabel != null ? subjectLabel.word() : head.word());
            if (subjectLabel != null) {
                var conjuncts = findConjuncts(graph, subjectLabel);
                if (!conjuncts.isEmpty()) {
                    StringBuilder combined = new StringBuilder(subjectToken);
                    for (CoreLabel conjunct : conjuncts) {
                        String token = normalizeToken(conjunct.word());
                        if (!token.isEmpty()) {
                            combined.append("_and_").append(token);
                        }
                    }
                    subjectToken = combined.toString();
                }
            }
            String objectToken = normalizeToken(object.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }

            Statement candidate = buildStatement(subjectToken, objectToken, predicate, false);
            String key = candidate.subject().value() + "|" + candidate.predicate() + "|" + candidate.object().value();
            statements.putIfAbsent(key, candidate);
        }
        if (statements.isEmpty()) {
            return Optional.empty();
        }
        java.util.List<Statement> all = new java.util.ArrayList<>(statements.values());
        Statement primary = selectPrimaryStatement(all);
        java.util.List<Statement> extras = new java.util.ArrayList<>(all);
        extras.remove(primary);
        if (extras.isEmpty()) {
            return Optional.of(primary);
        }
        return Optional.of(new Statement(
                primary.subject(),
                primary.object(),
                primary.predicate(),
                primary.subjectTypes(),
                primary.objectTypes(),
                primary.objectIsConcept(),
                primary.confidence(),
                extras
        ));
    }

    private Statement selectPrimaryStatement(java.util.List<Statement> statements) {
        for (Statement statement : statements) {
            if (isPreferredPredicate(statement.predicate())) {
                return statement;
            }
        }
        return statements.get(0);
    }

    private java.util.List<CoreLabel> findConjuncts(SemanticGraph graph, CoreLabel head) {
        var node = graph.getNodeByIndexSafe(head.index());
        if (node == null) {
            return java.util.List.of();
        }
        java.util.List<CoreLabel> conjuncts = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(node)) {
            if ("conj".equals(edge.getRelation().getShortName())) {
                conjuncts.add(edge.getDependent().backingLabel());
            }
        }
        return conjuncts;
    }

    private boolean isPreferredPredicate(String predicate) {
        return PREDICATE_AT.equals(predicate) || PREDICATE_IN.equals(predicate) || "inside".equals(predicate);
    }

    private CoreLabel findDependent(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor, String relation) {
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            if (relation.equals(edge.getRelation().getShortName())) {
                return edge.getDependent().backingLabel();
            }
        }
        return null;
    }

    private String findCase(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor) {
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            if ("case".equals(edge.getRelation().getShortName())) {
                return edge.getDependent().word().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private boolean hasDeterminer(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor) {
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            if ("det".equals(edge.getRelation().getShortName())) {
                return true;
            }
        }
        return false;
    }

    private String mapPreposition(String prep, String governorWord) {
        if ("in".equals(prep)) {
            return PREDICATE_IN;
        }
        if ("at".equals(prep)) {
            return PREDICATE_AT;
        }
        if ("of".equals(prep) && isPartGovernor(governorWord)) {
            return "partOf";
        }
        if ("by".equals(prep) && governorWord != null && !governorWord.isBlank()) {
            return governorWord.toLowerCase(Locale.ROOT) + "By";
        }
        return prep;
    }

    private boolean isPartGovernor(String governorWord) {
        if (governorWord == null) {
            return false;
        }
        String normalized = governorWord.toLowerCase(Locale.ROOT);
        return "part".equals(normalized) || "member".equals(normalized);
    }

    private Statement buildStatement(String subjectToken, String objectToken, String predicate, boolean objectIsConcept) {
        SymbolId subjectId = new SymbolId("entity:" + subjectToken);
        SymbolId objectId = new SymbolId((objectIsConcept ? "concept:" : "entity:") + objectToken);

        Set<String> subjectTypes = Set.of(subjectToken);
        Set<String> objectTypes = Set.of(objectToken);

        return new Statement(
                subjectId,
                objectId,
                predicate,
                subjectTypes,
                objectTypes,
                objectIsConcept,
                0.9
        );
    }

    private Optional<Statement> parseBinary(String normalized, String keyword, String predicate, boolean objectIsConcept) {
        String[] parts = normalized.split("\\s" + keyword + "\\s", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String subjectToken = normalizeToken(parts[0]);
        String objectToken = normalizeToken(parts[1]);
        if (subjectToken.isEmpty() || objectToken.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(buildStatement(subjectToken, objectToken, predicate, objectIsConcept));
    }

    private String normalizeToken(String raw) {
        String trimmed = raw.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (trimmed.startsWith("the ")) {
                trimmed = trimmed.substring(4);
                changed = true;
            } else if (trimmed.startsWith("a ")) {
                trimmed = trimmed.substring(2);
                changed = true;
            } else if (trimmed.startsWith("an ")) {
                trimmed = trimmed.substring(3);
                changed = true;
            }
        }
        if ("the".equals(trimmed) || "a".equals(trimmed) || "an".equals(trimmed)) {
            return "";
        }
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.trim().replaceAll("\\s+", "_");
    }

    private static StanfordCoreNLP buildPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("ssplit.isOneSentence", "true");
        return new StanfordCoreNLP(props);
    }
}
