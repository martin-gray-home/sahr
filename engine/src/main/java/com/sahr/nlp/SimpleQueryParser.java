package com.sahr.nlp;

import com.sahr.core.QueryGoal;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class SimpleQueryParser {
    private static final Set<String> WH_TOKENS = Set.of("who", "what", "where", "when", "why", "how", "which");
    private static final Set<String> YESNO_PREFIXES = Set.of("is", "are", "was", "were", "do", "does", "did", "can", "could", "should", "would", "will");
    private static final Set<String> PREPOSITION_RELATIONS = Set.of("on", "under", "above", "below", "with", "in", "inside", "opposite");
    private static final Set<String> COLOCATION_SYNONYMS = Set.of("near", "beside", "alongside", "next");
    private static final Set<String> COLOR_MODIFIERS = Set.of("red", "blue", "green", "black", "white");
    private static final StanfordCoreNLP PIPELINE = buildPipeline();

    public QueryGoal parse(String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT).trim();

        if (normalized.isEmpty()) {
            return QueryGoal.unknown();
        }

        if (!isQuestion(normalized)) {
            return QueryGoal.unknown();
        }

        Optional<QueryGoal> attribute = parseAttributeQuery(normalized);
        if (attribute.isPresent()) {
            return attribute.get();
        }

        if (normalized.startsWith("how many")) {
            Optional<QueryGoal> count = parseCountQuery(normalized);
            if (count.isPresent()) {
                return count.get();
            }
        }

        if (normalized.contains("where")) {
            String type = extractEntityType(normalized);
            if (type == null || type.isBlank()) {
                return QueryGoal.unknown();
            }
            return QueryGoal.where(type, "concept:location");
        }

        Optional<QueryGoal> yesNo = parseYesNoQuery(input);
        if (yesNo.isPresent()) {
            return yesNo.get();
        }

        Optional<QueryGoal> relation = parseRelationQuery(input);
        if (relation.isPresent()) {
            return relation.get();
        }

        return QueryGoal.unknown();
    }

    public boolean isQuestion(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.endsWith("?")) {
            return true;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) {
            return false;
        }
        String firstToken = tokens.get(0);
        return WH_TOKENS.contains(firstToken) || YESNO_PREFIXES.contains(firstToken);
    }

    private String extractEntityType(String normalized) {
        int idx = normalized.indexOf("where is");
        if (idx >= 0) {
            String remainder = normalized.substring(idx + "where is".length()).trim();
            if (!remainder.isEmpty()) {
                String token = normalizeToken(remainder);
                if (isPronoun(token)) {
                    return null;
                }
                return token;
            }
        }
        idx = normalized.indexOf("where was");
        if (idx >= 0) {
            String remainder = normalized.substring(idx + "where was".length()).trim();
            if (!remainder.isEmpty()) {
                String token = normalizeToken(remainder);
                if (isPronoun(token)) {
                    return null;
                }
                return token;
            }
        }
        idx = normalized.indexOf("where were");
        if (idx >= 0) {
            String remainder = normalized.substring(idx + "where were".length()).trim();
            if (!remainder.isEmpty()) {
                String token = normalizeToken(remainder);
                if (isPronoun(token)) {
                    return null;
                }
                return token;
            }
        }
        idx = normalized.indexOf("where are");
        if (idx >= 0) {
            String remainder = normalized.substring(idx + "where are".length()).trim();
            if (!remainder.isEmpty()) {
                String token = normalizeToken(remainder);
                if (isPronoun(token)) {
                    return null;
                }
                return token;
            }
        }

        if (normalized.contains("person")) {
            return "person";
        }
        if (normalized.contains("wife")) {
            return "wife";
        }
        if (normalized.contains("table")) {
            return "table";
        }
        return null;
    }

    private boolean isPronoun(String token) {
        return "it".equals(token)
                || "he".equals(token)
                || "she".equals(token)
                || "they".equals(token)
                || "them".equals(token)
                || "him".equals(token)
                || "her".equals(token)
                || "this".equals(token)
                || "that".equals(token)
                || "these".equals(token)
                || "those".equals(token);
    }

    private Optional<QueryGoal> parseRelationQuery(String input) {
        Annotation doc = new Annotation(input);
        PIPELINE.annotate(doc);
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            if (graph == null) {
                continue;
            }

            Optional<QueryGoal> objQuery = parseWhObjectQuery(graph);
            if (objQuery.isPresent()) {
                return objQuery;
            }

            Optional<QueryGoal> withQuery = parseWithQuery(graph);
            if (withQuery.isPresent()) {
                return withQuery;
            }

            Optional<QueryGoal> whSubject = parseWhSubjectQuery(graph);
            if (whSubject.isPresent()) {
                return whSubject;
            }

            Optional<QueryGoal> whPrepSubject = parseWhPrepositionSubjectQuery(graph);
            if (whPrepSubject.isPresent()) {
                return whPrepSubject;
            }
        }
        Optional<QueryGoal> powerFallback = parsePowerFallback(input);
        if (powerFallback.isPresent()) {
            return powerFallback;
        }
        Optional<QueryGoal> withFallback = parseWithFallback(input);
        if (withFallback.isPresent()) {
            return withFallback;
        }
        Optional<QueryGoal> whPrepFallback = parseWhPrepositionFallback(input);
        if (whPrepFallback.isPresent()) {
            return whPrepFallback;
        }
        return parsePrepositionFallback(input);
    }

    private Optional<QueryGoal> parsePowerFallback(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(normalized);
        if (tokens.size() < 3) {
            return Optional.empty();
        }
        if (!"what".equals(tokens.get(0))) {
            return Optional.empty();
        }
        String verb = tokens.get(1);
        if (!shouldInvertPowerVerb(verb)) {
            return Optional.empty();
        }
        String subjectToken = firstContentTokenAfter(tokens, 1);
        if (subjectToken == null || subjectToken.isBlank()) {
            return Optional.empty();
        }
        String base = verb.endsWith("s") ? verb.substring(0, verb.length() - 1) : verb;
        String predicate = toPassivePredicate(base);
        return Optional.of(QueryGoal.relation(subjectToken, predicate, null, expectedTypeForWh("what")));
    }

    private Optional<QueryGoal> parseAttributeQuery(String normalized) {
        if (!normalized.startsWith("what ")) {
            return Optional.empty();
        }
        int isIndex = normalized.indexOf(" is ");
        int areIndex = normalized.indexOf(" are ");
        int split = isIndex >= 0 ? isIndex : areIndex;
        if (split < 0) {
            return Optional.empty();
        }
        if (split <= 5) {
            return Optional.empty();
        }
        String attribute = normalized.substring(5, split).trim();
        if (attribute.isBlank()) {
            return Optional.empty();
        }
        if ("is".equals(attribute) || "are".equals(attribute) || WH_TOKENS.contains(attribute)) {
            return Optional.empty();
        }
        String remainder = normalized.substring(split + 4).trim();
        if (remainder.isBlank()) {
            return Optional.empty();
        }
        String subject = normalizeToken(remainder);
        if (isPronoun(subject)) {
            return Optional.empty();
        }
        return Optional.of(QueryGoal.attribute(subject, attribute));
    }

    private Optional<QueryGoal> parseCountQuery(String normalized) {
        List<String> tokens = tokenize(normalized);
        if (tokens.size() < 3 || !"how".equals(tokens.get(0)) || !"many".equals(tokens.get(1))) {
            return Optional.empty();
        }
        int subjectIndex = firstContentIndexAfter(tokens, 1);
        if (subjectIndex < 0) {
            return Optional.empty();
        }
        String subjectType = tokens.get(subjectIndex);
        if (isPronoun(subjectType)) {
            return Optional.empty();
        }
        int inIndex = tokens.indexOf("in");
        int withIndex = tokens.indexOf("with");
        int relationIndex = inIndex >= 0 ? inIndex : withIndex;
        if (relationIndex < 0) {
            return Optional.empty();
        }
        String predicate = "in".equals(tokens.get(relationIndex)) ? "locatedIn" : "with";
        int objectIndex = firstContentIndexAfter(tokens, relationIndex);
        if (objectIndex < 0) {
            return Optional.empty();
        }
        String object = tokens.get(objectIndex);
        String modifier = null;
        if (objectIndex > 0 && COLOR_MODIFIERS.contains(tokens.get(objectIndex - 1))) {
            modifier = tokens.get(objectIndex - 1);
        }
        return Optional.of(QueryGoal.count(null, predicate, object, subjectType, modifier));
    }

    private Optional<QueryGoal> parseYesNoQuery(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty() || !YESNO_PREFIXES.contains(tokens.get(0))) {
            return Optional.empty();
        }
        Optional<QueryGoal> preposition = parseYesNoPrepositionFallback(tokens);
        if (preposition.isPresent()) {
            return preposition;
        }
        Annotation doc = new Annotation(input);
        PIPELINE.annotate(doc);
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            if (graph == null) {
                continue;
            }

            for (SemanticGraphEdge edge : graph.edgeIterable()) {
                if (!"nsubj".equals(edge.getRelation().getShortName())) {
                    continue;
                }
                CoreLabel subject = edge.getDependent().backingLabel();
                CoreLabel verb = edge.getGovernor().backingLabel();
                CoreLabel object = findDependent(graph, edge.getGovernor(), "obj");
                if (object == null) {
                    continue;
                }

                String subjectToken = normalizeToken(subject.word());
                String objectToken = normalizeToken(object.word());
                if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                    continue;
                }

                String predicate = verb.lemma().toLowerCase(Locale.ROOT);
                String predicateText = "is " + verb.word().toLowerCase(Locale.ROOT);
                String subjectText = withDeterminer(graph, subject);
                String objectText = withDeterminer(graph, object);
                return Optional.of(QueryGoal.yesNo(subjectToken, predicate, objectToken, null, subjectText, objectText, predicateText));
            }
        }
        return Optional.empty();
    }

    private Optional<QueryGoal> parseWhObjectQuery(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"obj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel object = edge.getDependent().backingLabel();
            String wh = normalizeToken(object.lemma());
            if (!WH_TOKENS.contains(wh)) {
                continue;
            }
            CoreLabel verb = edge.getGovernor().backingLabel();
            CoreLabel subject = findDependent(graph, edge.getGovernor(), "nsubj");
            if (subject == null) {
                continue;
            }

            String subjectToken = normalizeToken(subject.word());
            if (subjectToken.isEmpty()) {
                continue;
            }
            String predicate = verb.lemma().toLowerCase(Locale.ROOT);

            return Optional.of(QueryGoal.relation(subjectToken, predicate, null, expectedTypeForWh(wh)));
        }
        return Optional.empty();
    }

    private Optional<QueryGoal> parseWithQuery(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null || !"with".equalsIgnoreCase(specific)) {
                continue;
            }
            CoreLabel whSubject = findDependent(graph, edge.getGovernor(), "nsubj");
            if (whSubject == null) {
                continue;
            }
            String wh = normalizeToken(whSubject.lemma());
            if (!WH_TOKENS.contains(wh)) {
                continue;
            }

            CoreLabel target = edge.getDependent().backingLabel();
            String subjectToken = normalizeToken(target.word());
            if (subjectToken.isEmpty()) {
                continue;
            }
            String modifier = findAdjectiveModifier(graph, target);
            return Optional.of(QueryGoal.relationWithModifier(subjectToken, "with", null, expectedTypeForWh(wh), modifier));
        }
        return Optional.empty();
    }

    private Optional<QueryGoal> parseWhSubjectQuery(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"nsubj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel subject = edge.getDependent().backingLabel();
            String wh = normalizeToken(subject.lemma());
            if (!WH_TOKENS.contains(wh)) {
                continue;
            }
            CoreLabel verb = edge.getGovernor().backingLabel();
            CoreLabel object = findDependent(graph, edge.getGovernor(), "obj");
            if (object == null) {
                continue;
            }
            String objectToken = normalizeToken(object.word());
            if (objectToken.isEmpty()) {
                continue;
            }
            String predicate = verb.lemma().toLowerCase(Locale.ROOT);
            if ("what".equals(wh) && shouldInvertPowerVerb(predicate)) {
                String inverted = toPassivePredicate(predicate);
                return Optional.of(QueryGoal.relation(objectToken, inverted, null, expectedTypeForWh(wh)));
            }

            return Optional.of(QueryGoal.relation(null, predicate, objectToken, expectedTypeForWh(wh)));
        }
        return Optional.empty();
    }

    private Optional<QueryGoal> parseWithFallback(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(normalized);
        if (!tokens.contains("with")) {
            return Optional.empty();
        }
        String wh = tokens.stream().filter(WH_TOKENS::contains).findFirst().orElse(null);
        if (wh == null) {
            return Optional.empty();
        }
        int withIndex = tokens.indexOf("with");
        SubjectModifier subjectModifier = subjectModifierAfter(tokens, withIndex);
        String subjectToken = subjectModifier.subject;
        if (subjectToken == null) {
            subjectToken = firstContentTokenBefore(tokens, withIndex);
        }
        if (subjectToken == null || subjectToken.isBlank()) {
            return Optional.empty();
        }
        String modifier = subjectModifier.modifier != null ? subjectModifier.modifier : modifierBefore(tokens, subjectToken);
        return Optional.of(QueryGoal.relationWithModifier(subjectToken, "with", null, expectedTypeForWh(wh), modifier));
    }

    private Optional<QueryGoal> parsePrepositionFallback(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(normalized);
        String wh = tokens.stream().filter(WH_TOKENS::contains).findFirst().orElse(null);
        if (wh == null) {
            return Optional.empty();
        }
        int onIndex = tokens.indexOf("on");
        int underIndex = tokens.indexOf("under");
        int prepIndex = onIndex >= 0 ? onIndex : underIndex;
        if (prepIndex < 0) {
            return Optional.empty();
        }
        String subjectToken = firstContentTokenAfter(tokens, prepIndex);
        if (subjectToken == null) {
            return Optional.empty();
        }
        String predicate = onIndex >= 0 ? "on" : "under";
        String modifier = modifierBefore(tokens, subjectToken);
        return Optional.of(QueryGoal.relationWithModifier(subjectToken, predicate, null, expectedTypeForWh(wh), modifier));
    }

    private Optional<QueryGoal> parseWhPrepositionSubjectQuery(SemanticGraph graph) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null) {
                continue;
            }
            String prep = specific.toLowerCase(Locale.ROOT);
            if (!PREPOSITION_RELATIONS.contains(prep)) {
                continue;
            }
            CoreLabel whSubject = findDependent(graph, edge.getGovernor(), "nsubj");
            if (whSubject == null) {
                continue;
            }
            String wh = normalizeToken(whSubject.lemma());
            if (!WH_TOKENS.contains(wh)) {
                continue;
            }

            CoreLabel object = edge.getDependent().backingLabel();
            String objectToken = normalizeToken(object.word());
            if (objectToken.isEmpty()) {
                continue;
            }

            String predicate = mapPrepositionPredicate(prep);
            String modifier = findAdjectiveModifier(graph, object);
            return Optional.of(QueryGoal.relationWithModifier(null, predicate, objectToken, expectedTypeForWh(wh), modifier));
        }
        return Optional.empty();
    }

    private Optional<QueryGoal> parseWhPrepositionFallback(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(normalized);
        String wh = tokens.stream().filter(WH_TOKENS::contains).findFirst().orElse(null);
        if (wh == null) {
            return Optional.empty();
        }
        int prepIndex = -1;
        String prep = null;
        for (String candidate : PREPOSITION_RELATIONS) {
            int idx = tokens.indexOf(candidate);
            if (idx >= 0) {
                prepIndex = idx;
                prep = candidate;
                break;
            }
        }
        if (prepIndex < 0 || prep == null) {
            return Optional.empty();
        }
        SubjectModifier objectModifier = subjectModifierAfter(tokens, prepIndex);
        String objectToken = objectModifier.subject;
        if (objectToken == null) {
            return Optional.empty();
        }
        String predicate = mapPrepositionPredicate(prep);
        return Optional.of(QueryGoal.relationWithModifier(null, predicate, objectToken, expectedTypeForWh(wh), objectModifier.modifier));
    }

    private String mapPrepositionPredicate(String prep) {
        if ("in".equals(prep)) {
            return "locatedIn";
        }
        return prep;
    }

    private boolean shouldInvertPowerVerb(String verb) {
        if (verb == null || verb.isBlank()) {
            return false;
        }
        String normalized = verb.endsWith("s") ? verb.substring(0, verb.length() - 1) : verb;
        return "power".equals(normalized) || "charge".equals(normalized);
    }

    private String toPassivePredicate(String verb) {
        if (verb == null || verb.isBlank()) {
            return verb;
        }
        if (verb.endsWith("e")) {
            return verb + "dBy";
        }
        return verb + "edBy";
    }

    private Optional<QueryGoal> parseYesNoPrepositionFallback(List<String> tokens) {
        int relationIndex = -1;
        String relation = null;
        for (String candidate : PREPOSITION_RELATIONS) {
            int idx = tokens.indexOf(candidate);
            if (idx >= 0) {
                relationIndex = idx;
                relation = candidate;
                break;
            }
        }
        if (relationIndex < 0 || relation == null) {
            return Optional.empty();
        }
        int subjectIndex = firstContentIndexAfter(tokens, 0);
        int objectIndex = firstContentIndexAfter(tokens, relationIndex);
        String subject = subjectIndex < 0 ? null : tokens.get(subjectIndex);
        String object = objectIndex < 0 ? null : tokens.get(objectIndex);
        if (subject == null || object == null) {
            return Optional.empty();
        }

        String subjectText = buildPhrase(tokens, subjectIndex);
        String objectText = buildPhrase(tokens, objectIndex);

        return Optional.of(QueryGoal.yesNo(subject, relation, object, null, subjectText, objectText, relation));
    }

    private int firstContentIndexAfter(List<String> tokens, int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("the".equals(token) || "a".equals(token) || "an".equals(token)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private String buildPhrase(List<String> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return "";
        }
        String token = tokens.get(index);
        if (index > 0) {
            String prev = tokens.get(index - 1);
            if ("the".equals(prev) || "a".equals(prev) || "an".equals(prev)) {
                return prev + " " + token;
            }
        }
        return token;
    }

    private String findAdjectiveModifier(SemanticGraph graph, CoreLabel head) {
        if (head == null) {
            return null;
        }
        var node = graph.getNodeByIndexSafe(head.index());
        if (node == null) {
            return null;
        }
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(node)) {
            if ("amod".equals(edge.getRelation().getShortName())) {
                return normalizeToken(edge.getDependent().word());
            }
        }
        return null;
    }

    private String modifierBefore(List<String> tokens, String token) {
        int idx = tokens.indexOf(token);
        if (idx <= 0) {
            return null;
        }
        String prev = tokens.get(idx - 1);
        if ("the".equals(prev) || "a".equals(prev) || "an".equals(prev)) {
            if (idx - 2 >= 0) {
                prev = tokens.get(idx - 2);
            } else {
                return null;
            }
        }
        if (!COLOR_MODIFIERS.contains(prev)) {
            return null;
        }
        return prev;
    }

    private SubjectModifier subjectModifierAfter(List<String> tokens, int index) {
        int first = firstContentIndexAfter(tokens, index);
        if (first < 0) {
            return new SubjectModifier(null, null);
        }
        String candidate = tokens.get(first);
        if (COLOR_MODIFIERS.contains(candidate)) {
            int next = firstContentIndexAfter(tokens, first);
            if (next >= 0) {
                return new SubjectModifier(tokens.get(next), candidate);
            }
        }
        return new SubjectModifier(candidate, null);
    }

    private static final class SubjectModifier {
        private final String subject;
        private final String modifier;

        private SubjectModifier(String subject, String modifier) {
            this.subject = subject;
            this.modifier = modifier;
        }
    }

    private CoreLabel findDependent(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor, String relation) {
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(governor)) {
            if (relation.equals(edge.getRelation().getShortName())) {
                return edge.getDependent().backingLabel();
            }
        }
        return null;
    }

    private String withDeterminer(SemanticGraph graph, CoreLabel head) {
        String det = null;
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(graph.getNodeByIndexSafe(head.index()))) {
            if ("det".equals(edge.getRelation().getShortName())) {
                det = edge.getDependent().word().toLowerCase(Locale.ROOT);
                break;
            }
        }
        String token = normalizeToken(head.word()).replace('_', ' ');
        if (det != null && !det.isBlank()) {
            return det + " " + token;
        }
        return token;
    }

    private String expectedTypeForWh(String wh) {
        if ("who".equals(wh)) {
            return "person";
        }
        if ("where".equals(wh)) {
            return "concept:location";
        }
        return "entity";
    }

    private String normalizeToken(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("the ")) {
            trimmed = trimmed.substring(4);
        } else if (trimmed.startsWith("a ")) {
            trimmed = trimmed.substring(2);
        } else if (trimmed.startsWith("an ")) {
            trimmed = trimmed.substring(3);
        }
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.trim().replaceAll("\\s+", "_");
    }

    private List<String> tokenize(String normalized) {
        String cleaned = normalized.replaceAll("[^a-z0-9\\s]", " ").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> tokens = List.of(cleaned.split("\\s+"));
        return normalizeColocationSynonyms(tokens);
    }

    private List<String> normalizeColocationSynonyms(List<String> tokens) {
        if (tokens.isEmpty()) {
            return tokens;
        }
        java.util.List<String> normalized = new java.util.ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("next".equals(token) && i + 1 < tokens.size() && "to".equals(tokens.get(i + 1))) {
                normalized.add("with");
                i++;
                continue;
            }
            if (COLOCATION_SYNONYMS.contains(token)) {
                normalized.add("with");
                continue;
            }
            normalized.add(token);
        }
        return normalized;
    }

    private String firstContentTokenAfter(List<String> tokens, int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("the".equals(token) || "a".equals(token) || "an".equals(token)) {
                continue;
            }
            return token;
        }
        return null;
    }

    private String firstContentTokenBefore(List<String> tokens, int index) {
        for (int i = index - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if ("the".equals(token) || "a".equals(token) || "an".equals(token)) {
                continue;
            }
            return token;
        }
        return null;
    }

    private static StanfordCoreNLP buildPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("ssplit.isOneSentence", "true");
        return new StanfordCoreNLP(props);
    }
}
