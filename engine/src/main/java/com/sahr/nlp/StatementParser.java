package com.sahr.nlp;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import com.sahr.core.SymbolId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class StatementParser {
    private static final String PREDICATE_AT = "at";
    private static final String PREDICATE_IN = "locatedIn";
    private static final String PREDICATE_TYPE = "rdf:type";
    private static final String PREDICATE_ATTRIBUTE = "hasAttribute";
    private static final String PREDICATE_MANNER = "hasManner";
    private static final Set<String> PREPOSITION_PREDICATES = Set.of(
            "inside",
            "on",
            "under",
            "with",
            "opposite",
            "near",
            "beside",
            "alongside",
            "next",
            "next_to",
            "next-to"
    );

    private static final StanfordCoreNLP PIPELINE = buildPipeline();
    private static final Morphology MORPHOLOGY = new Morphology();
    private static final DependencySemanticMapper SEMANTIC_MAPPER = new DependencySemanticMapper();

    private final boolean ontologyDriven;

    public StatementParser() {
        this(true);
    }

    public StatementParser(boolean ontologyDriven) {
        this.ontologyDriven = ontologyDriven;
    }

    public Optional<Statement> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        Optional<Statement> coreNlp = parseWithCoreNlp(trimmed);
        Optional<Statement> passive = parsePassiveBy(normalized);
        if (coreNlp.isPresent() && passive.isPresent()) {
            return Optional.of(mergeStatements(coreNlp.get(), passive.get()));
        }
        if (coreNlp.isPresent()) {
            return coreNlp;
        }
        if (normalized.contains(" is inside ")) {
            return parseBinary(normalized, "is inside", mapPreposition("inside", null), false);
        }
        if (normalized.contains(" is in ")) {
            return parseBinary(normalized, "is in", mapPreposition("in", null), false);
        }
        if (normalized.contains(" is at ")) {
            return parseBinary(normalized, "is at", mapPreposition("at", null), false);
        }
        if (normalized.contains(" is with ")) {
            return parseBinary(normalized, "is with", mapPreposition("with", null), false);
        }
        if (normalized.contains(" is near ")) {
            return parseBinary(normalized, "is near", mapPreposition("near", null), false);
        }
        if (normalized.contains(" is beside ")) {
            return parseBinary(normalized, "is beside", mapPreposition("beside", null), false);
        }
        if (normalized.contains(" is alongside ")) {
            return parseBinary(normalized, "is alongside", mapPreposition("alongside", null), false);
        }
        if (normalized.contains(" is next to ")) {
            return parseBinary(normalized, "is next to", mapPreposition("next_to", null), false);
        }
        if (normalized.contains(" is next-to ")) {
            return parseBinary(normalized, "is next-to", mapPreposition("next-to", null), false);
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
        if (passive.isPresent()) {
            return passive;
        }
        if (normalized.contains(" is a ") || normalized.contains(" is an ")) {
            return parseBinary(normalized, normalized.contains(" is a ") ? "is a" : "is an", PREDICATE_TYPE, true);
        }

        return Optional.empty();
    }

    private Optional<Statement> parsePassiveBy(String normalized) {
        CopulaMatch copula = findPassiveCopula(normalized);
        int byIndex = normalized.indexOf(" by ");
        if (copula == null || byIndex < 0 || byIndex <= copula.index + copula.length) {
            return Optional.empty();
        }
        String subjectPart = normalized.substring(0, copula.index).trim();
        String verbPart = normalized.substring(copula.index + copula.length, byIndex).trim();
        String objectPart = normalized.substring(byIndex + 4).trim();
        if (subjectPart.isBlank() || verbPart.isBlank() || objectPart.isBlank()) {
            return Optional.empty();
        }
        String patientToken = normalizeToken(subjectPart);
        String agentToken = normalizeToken(objectPart);
        if (patientToken.isEmpty() || agentToken.isEmpty()) {
            return Optional.empty();
        }
        String passivePredicate = verbPart.replaceAll("\\s+", "_") + "By";
        Statement passive = buildStatement(patientToken, agentToken, passivePredicate, false);
        Statement active = buildStatement(agentToken, patientToken, normalizePassiveVerb(verbPart), false);
        return Optional.of(new Statement(
                passive.subject(),
                passive.object(),
                passive.predicate(),
                passive.subjectTypes(),
                passive.objectTypes(),
                passive.objectIsConcept(),
                passive.confidence(),
                java.util.List.of(active)
        ));
    }

    private static CopulaMatch findPassiveCopula(String normalized) {
        String[] candidates = {" is ", " was ", " were ", " are ", " be "};
        for (String candidate : candidates) {
            int index = normalized.indexOf(candidate);
            if (index >= 0) {
                return new CopulaMatch(index, candidate.length());
            }
        }
        return null;
    }

    private static final class CopulaMatch {
        private final int index;
        private final int length;

        private CopulaMatch(int index, int length) {
            this.index = index;
            this.length = length;
        }
    }

    private String normalizePassiveVerb(String verbPart) {
        String[] parts = verbPart.trim().split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        String lemma = MORPHOLOGY.lemma(parts[0], "V");
        String head = (lemma == null || lemma.isBlank()) ? parts[0] : lemma;
        StringBuilder normalized = new StringBuilder(head.toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            normalized.append('_').append(parts[i].toLowerCase(Locale.ROOT));
        }
        return normalized.toString();
    }

    private Optional<Statement> parseWithCoreNlp(String input) {
        Annotation doc = new Annotation(input);
        PIPELINE.annotate(doc);
        java.util.Map<String, Statement> collected = new java.util.LinkedHashMap<>();
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            if (graph == null) {
                continue;
            }
            addStatements(collected, parseCopularStatements(graph));
            addStatements(collected, parseVerbObjectStatements(graph));
            addStatements(collected, parseIntransitiveStatements(graph));
            addStatements(collected, parseNmodStatements(graph));
            addStatements(collected, parseAdjectivalStatements(graph));
            addStatements(collected, parseAdverbStatements(graph));
            addStatements(collected, parseRelativeClauseStatements(graph));
            addStatements(collected, parseAclStatements(graph));
            addStatements(collected, parseClausalComplementStatements(graph));
            addStatements(collected, parseApposStatements(graph));
            addStatements(collected, SEMANTIC_MAPPER.map(graph, new Helper()));
        }
        if (collected.isEmpty()) {
            return Optional.empty();
        }
        java.util.List<Statement> all = new java.util.ArrayList<>(collected.values());
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

    private List<Statement> parseCopularStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
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
                continue;
            }
            String predicateType = preposition != null ? mapPreposition(preposition, predicate.word()) : PREDICATE_TYPE;
            boolean objectIsConcept = PREDICATE_TYPE.equals(predicateType);

            String subjectToken = normalizeToken(composeCompoundToken(graph, subject));
            String baseSubjectToken = normalizeToken(subject.word());
            var conjuncts = collectConjuncts(graph, subject);
            if (!conjuncts.isEmpty()) {
                StringBuilder combined = new StringBuilder(subjectToken);
                for (CoreLabel conjunct : conjuncts) {
                    String token = normalizeToken(composeCompoundToken(graph, conjunct));
                    if (!token.isEmpty()) {
                        combined.append("_and_").append(token);
                    }
                }
                subjectToken = combined.toString();
            }
            CoreLabel prepObject = preposition == null ? null : findPrepositionalObject(graph, edge.getGovernor(), preposition);
            if (prepObject == null && prepMatch != null) {
                prepObject = prepMatch.object();
            }
            CoreLabel objectLabel = prepObject != null ? prepObject : predicate;
            String objectToken = normalizeToken(composeCompoundToken(graph, objectLabel));
            String baseObjectToken = normalizeToken(objectLabel.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            if (preposition == null && hasDeterminer(graph, edge.getGovernor())) {
                continue;
            }
            java.util.Set<String> subjectTokens = new java.util.LinkedHashSet<>();
            if (!baseSubjectToken.isEmpty() && !baseSubjectToken.equals(subjectToken)) {
                subjectTokens.add(baseSubjectToken);
            }
            subjectTokens.add(subjectToken);
            java.util.Set<String> objectTokens = new java.util.LinkedHashSet<>();
            if (!baseObjectToken.isEmpty() && !baseObjectToken.equals(objectToken)) {
                objectTokens.add(baseObjectToken);
            }
            objectTokens.add(objectToken);
            for (String subjectEntry : subjectTokens) {
                for (String objectEntry : objectTokens) {
                    statements.add(buildStatement(subjectEntry, objectEntry, predicateType, objectIsConcept));
                }
            }
            List<CoreLabel> objectConjuncts = findConjuncts(graph, prepObject != null ? prepObject : predicate);
            for (CoreLabel conjunct : objectConjuncts) {
                String conjunctToken = normalizeToken(composeCompoundToken(graph, conjunct));
                if (!conjunctToken.isEmpty()) {
                    for (String subjectEntry : subjectTokens) {
                        statements.add(buildStatement(subjectEntry, conjunctToken, predicateType, objectIsConcept));
                    }
                }
            }
        }
        return statements;
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

    private List<Statement> parseVerbObjectStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"obj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord verbNode = edge.getGovernor();
            CoreLabel verb = verbNode.backingLabel();
            CoreLabel object = edge.getDependent().backingLabel();
            CoreLabel subject = resolveVerbSubject(graph, verbNode);
            if (subject == null) {
                continue;
            }

            String subjectToken = normalizeToken(composeCompoundToken(graph, subject));
            String objectToken = normalizeToken(composeCompoundToken(graph, object));
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            if (subjectToken.endsWith("_by")) {
                subjectToken = subjectToken.substring(0, subjectToken.length() - 3);
            }

            String predicate = resolveVerbPredicate(graph, verbNode);
            statements.add(buildStatement(subjectToken, objectToken, predicate, false));
            CoreLabel indirect = findIndirectObject(graph, verbNode);
            if (indirect != null) {
                String indirectToken = normalizeToken(composeCompoundToken(graph, indirect));
                if (!indirectToken.isEmpty()) {
                    statements.add(buildStatement(subjectToken, indirectToken, predicate, false));
                }
            }
            for (CoreLabel conjunctVerb : findConjuncts(graph, verb)) {
                String conjPredicate = resolveVerbPredicate(graph, conjunctVerb);
                if (!conjPredicate.isEmpty()) {
                    statements.add(buildStatement(subjectToken, objectToken, conjPredicate, false));
                }
            }
            for (CoreLabel conjunct : findConjuncts(graph, object)) {
                String token = normalizeToken(composeCompoundToken(graph, conjunct));
                if (!token.isEmpty()) {
                    statements.add(buildStatement(subjectToken, token, predicate, false));
                }
            }
        }
        return statements;
    }

    private List<Statement> parseIntransitiveStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"nsubj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord verbNode = edge.getGovernor();
            CoreLabel verb = verbNode.backingLabel();
            if (verb == null || verb.tag() == null || !verb.tag().startsWith("V")) {
                continue;
            }
            if (hasDependent(graph, verbNode, "cop")) {
                continue;
            }
            if (findDependent(graph, verbNode, "obj") != null) {
                continue;
            }
            CoreLabel subject = edge.getDependent().backingLabel();
            String subjectToken = normalizeToken(composeCompoundToken(graph, subject));
            if (subjectToken.isEmpty()) {
                continue;
            }
            String predicate = resolveVerbPredicate(graph, verbNode);
            if (predicate.isEmpty()) {
                continue;
            }
            String neg = hasDependent(graph, verbNode, "neg") ? "false" : "true";
            String objectToken = neg;

            CoreLabel xcomp = findDependent(graph, verbNode, "xcomp");
            if (xcomp != null) {
                String xcompVerb = normalizeToken(xcomp.word());
                if (!xcompVerb.isEmpty()) {
                    predicate = predicate + "_" + xcompVerb;
                }
            }
            statements.add(buildStatement(subjectToken, objectToken, predicate, true));
        }
        return statements;
    }

    private CoreLabel findIndirectObject(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord verbNode) {
        if (graph == null || verbNode == null) {
            return null;
        }
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(verbNode)) {
            String relation = edge.getRelation().getShortName();
            if ("iobj".equals(relation)) {
                return edge.getDependent().backingLabel();
            }
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific != null && "to".equalsIgnoreCase(specific)) {
                return edge.getDependent().backingLabel();
            }
        }
        return null;
    }

    private List<Statement> parseNmodStatements(SemanticGraph graph) {
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

            CoreLabel subjectSource = subjectLabel != null ? subjectLabel : head;
            String subjectToken = normalizeToken(composeCompoundToken(graph, subjectSource));
            String baseSubjectToken = normalizeToken(subjectSource.word());
            var conjuncts = collectConjuncts(graph, subjectSource);
            if (!conjuncts.isEmpty()) {
                StringBuilder combined = new StringBuilder(subjectToken);
                for (CoreLabel conjunct : conjuncts) {
                    String token = normalizeToken(composeCompoundToken(graph, conjunct));
                    if (!token.isEmpty()) {
                        combined.append("_and_").append(token);
                    }
                }
                subjectToken = combined.toString();
            }
            String objectToken = normalizeToken(composeCompoundToken(graph, object));
            String baseObjectToken = normalizeToken(object.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }

            if (isReciprocalOpposite(predicate, objectToken) && subjectLabel != null) {
                List<Statement> reciprocal = buildReciprocalOpposite(subjectLabel, graph);
                if (!reciprocal.isEmpty()) {
                    for (Statement statement : reciprocal) {
                        String key = statement.subject().value() + "|" + statement.predicate() + "|" + statement.object().value();
                        statements.putIfAbsent(key, statement);
                    }
                    continue;
                }
            }

            java.util.Set<String> subjectTokens = new java.util.LinkedHashSet<>();
            if (!baseSubjectToken.isEmpty() && !baseSubjectToken.equals(subjectToken)) {
                subjectTokens.add(baseSubjectToken);
            }
            subjectTokens.add(subjectToken);
            java.util.Set<String> objectTokens = new java.util.LinkedHashSet<>();
            if (!baseObjectToken.isEmpty() && !baseObjectToken.equals(objectToken)) {
                objectTokens.add(baseObjectToken);
            }
            objectTokens.add(objectToken);
            for (String subjectEntry : subjectTokens) {
                for (String objectEntry : objectTokens) {
                    Statement candidate = buildStatement(subjectEntry, objectEntry, predicate, false);
                    String key = candidate.subject().value() + "|" + candidate.predicate() + "|" + candidate.object().value();
                    statements.putIfAbsent(key, candidate);
                }
            }
        }
        return new java.util.ArrayList<>(statements.values());
    }

    private List<Statement> parseAdjectivalStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"amod".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel noun = edge.getGovernor().backingLabel();
            CoreLabel adjective = edge.getDependent().backingLabel();
            String baseSubject = normalizeToken(noun.word());
            String subjectToken = normalizeToken(composeCompoundToken(graph, noun));
            String objectToken = normalizeToken(adjective.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            statements.add(buildStatement(subjectToken, objectToken, PREDICATE_ATTRIBUTE, false));
            if (!baseSubject.isEmpty() && !baseSubject.equals(subjectToken)) {
                statements.add(buildStatement(baseSubject, objectToken, PREDICATE_ATTRIBUTE, false));
            }
        }
        return statements;
    }

    private List<Statement> parseAdverbStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"advmod".equals(edge.getRelation().getShortName())) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord governor = edge.getGovernor();
            CoreLabel subject = resolveVerbSubject(graph, governor);
            if (subject == null) {
                continue;
            }
            String subjectToken = normalizeToken(composeCompoundToken(graph, subject));
            String adverbToken = normalizeToken(edge.getDependent().word());
            if (subjectToken.isEmpty() || adverbToken.isEmpty()) {
                continue;
            }
            statements.add(buildStatement(subjectToken, adverbToken, PREDICATE_MANNER, false));
        }
        return statements;
    }

    private List<Statement> parseRelativeClauseStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"acl".equals(edge.getRelation().getShortName())) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null || !"relcl".equals(specific)) {
                continue;
            }
            CoreLabel subjectLabel = edge.getGovernor().backingLabel();
            edu.stanford.nlp.ling.IndexedWord clauseVerb = edge.getDependent();
            String subjectToken = normalizeToken(composeCompoundToken(graph, subjectLabel));
            if (subjectToken.isEmpty()) {
                continue;
            }
            for (SemanticGraphEdge verbEdge : graph.outgoingEdgeList(clauseVerb)) {
                String relation = verbEdge.getRelation().getShortName();
                if ("obj".equals(relation)) {
                    CoreLabel object = verbEdge.getDependent().backingLabel();
                    String objectToken = normalizeToken(composeCompoundToken(graph, object));
                    if (!objectToken.isEmpty()) {
                        String predicate = resolveVerbPredicate(graph, clauseVerb);
                        statements.add(buildStatement(subjectToken, objectToken, predicate, false));
                    }
                }
                if ("nmod".equals(relation) || "obl".equals(relation)) {
                    String prep = verbEdge.getRelation().getSpecific();
                    if (prep == null || prep.isBlank()) {
                        continue;
                    }
                    CoreLabel object = verbEdge.getDependent().backingLabel();
                    String objectToken = normalizeToken(composeCompoundToken(graph, object));
                    if (objectToken.isEmpty()) {
                        continue;
                    }
                    String predicate = mapPreposition(prep.toLowerCase(Locale.ROOT), clauseVerb.word());
                    statements.add(buildStatement(subjectToken, objectToken, predicate, false));
                }
            }
        }
        return statements;
    }

    private List<Statement> parseAclStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"acl".equals(edge.getRelation().getShortName())) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if ("relcl".equals(specific)) {
                continue;
            }
            CoreLabel subjectLabel = edge.getGovernor().backingLabel();
            edu.stanford.nlp.ling.IndexedWord clauseVerb = edge.getDependent();
            String subjectToken = normalizeToken(composeCompoundToken(graph, subjectLabel));
            if (subjectToken.isEmpty()) {
                continue;
            }
            String predicate = resolveVerbPredicate(graph, clauseVerb);
            for (SemanticGraphEdge verbEdge : graph.outgoingEdgeList(clauseVerb)) {
                String relation = verbEdge.getRelation().getShortName();
                if ("obj".equals(relation)) {
                    String objectToken = normalizeToken(composeCompoundToken(graph, verbEdge.getDependent().backingLabel()));
                    if (!objectToken.isEmpty()) {
                        statements.add(buildStatement(subjectToken, objectToken, predicate, false));
                    }
                }
                if ("nmod".equals(relation) || "obl".equals(relation)) {
                    String prep = verbEdge.getRelation().getSpecific();
                    if (prep == null || prep.isBlank()) {
                        continue;
                    }
                    String objectToken = normalizeToken(composeCompoundToken(graph, verbEdge.getDependent().backingLabel()));
                    if (objectToken.isEmpty()) {
                        continue;
                    }
                    String mapped = mapPreposition(prep.toLowerCase(Locale.ROOT), clauseVerb.word());
                    statements.add(buildStatement(subjectToken, objectToken, mapped, false));
                }
            }
        }
        return statements;
    }

    private List<Statement> parseClausalComplementStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"xcomp".equals(relation) && !"ccomp".equals(relation)) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord governor = edge.getGovernor();
            CoreLabel subject = resolveVerbSubject(graph, governor);
            if (subject == null) {
                continue;
            }
            String subjectToken = normalizeToken(composeCompoundToken(graph, subject));
            if (subjectToken.isEmpty()) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord complement = edge.getDependent();
            String predicate = resolveVerbPredicate(graph, complement);
            for (SemanticGraphEdge verbEdge : graph.outgoingEdgeList(complement)) {
                String childRel = verbEdge.getRelation().getShortName();
                if ("obj".equals(childRel)) {
                    String objectToken = normalizeToken(composeCompoundToken(graph, verbEdge.getDependent().backingLabel()));
                    if (!objectToken.isEmpty()) {
                        statements.add(buildStatement(subjectToken, objectToken, predicate, false));
                    }
                }
                if ("nmod".equals(childRel) || "obl".equals(childRel)) {
                    String prep = verbEdge.getRelation().getSpecific();
                    if (prep == null || prep.isBlank()) {
                        continue;
                    }
                    String objectToken = normalizeToken(composeCompoundToken(graph, verbEdge.getDependent().backingLabel()));
                    if (objectToken.isEmpty()) {
                        continue;
                    }
                    String mapped = mapPreposition(prep.toLowerCase(Locale.ROOT), complement.word());
                    statements.add(buildStatement(subjectToken, objectToken, mapped, false));
                }
            }
        }
        return statements;
    }

    private List<Statement> parseApposStatements(SemanticGraph graph) {
        java.util.List<Statement> statements = new java.util.ArrayList<>();
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"appos".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel subject = edge.getGovernor().backingLabel();
            CoreLabel object = edge.getDependent().backingLabel();
            String subjectToken = normalizeToken(subject.word());
            String objectToken = normalizeToken(object.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            statements.add(buildStatement(subjectToken, objectToken, PREDICATE_TYPE, true));
        }
        return statements;
    }

    private boolean isReciprocalOpposite(String predicate, String objectToken) {
        if (!"opposite".equals(predicate)) {
            return false;
        }
        return "other".equals(objectToken) || "each_other".equals(objectToken);
    }

    private List<Statement> buildReciprocalOpposite(CoreLabel subjectLabel, SemanticGraph graph) {
        if (subjectLabel == null) {
            return List.of();
        }
        List<CoreLabel> subjects = new java.util.ArrayList<>();
        subjects.add(subjectLabel);
        subjects.addAll(findConjuncts(graph, subjectLabel));
        if (subjects.size() < 2) {
            return List.of();
        }
        List<Statement> statements = new java.util.ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            for (int j = i + 1; j < subjects.size(); j++) {
                String left = normalizeToken(subjects.get(i).word());
                String right = normalizeToken(subjects.get(j).word());
                if (left.isEmpty() || right.isEmpty()) {
                    continue;
                }
                statements.add(buildStatement(left, right, "opposite", false));
                statements.add(buildStatement(right, left, "opposite", false));
            }
        }
        return statements;
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

    private java.util.List<CoreLabel> collectConjuncts(SemanticGraph graph, CoreLabel head) {
        var node = graph.getNodeByIndexSafe(head.index());
        if (node == null) {
            return java.util.List.of();
        }
        java.util.LinkedHashSet<CoreLabel> results = new java.util.LinkedHashSet<>();
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(node)) {
            if ("conj".equals(edge.getRelation().getShortName())) {
                results.add(edge.getDependent().backingLabel());
            }
        }
        for (SemanticGraphEdge edge : graph.incomingEdgeList(node)) {
            if ("conj".equals(edge.getRelation().getShortName())) {
                results.add(edge.getGovernor().backingLabel());
            }
        }
        results.remove(head);
        return new java.util.ArrayList<>(results);
    }

    private CoreLabel resolveVerbSubject(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord verbNode) {
        CoreLabel subject = findDependent(graph, verbNode, "nsubj");
        if (subject != null) {
            return subject;
        }
        CoreLabel passive = findDependent(graph, verbNode, "nsubj:pass");
        if (passive != null) {
            return passive;
        }
        for (SemanticGraphEdge incoming : graph.incomingEdgeList(verbNode)) {
            if (!"conj".equals(incoming.getRelation().getShortName())) {
                continue;
            }
            edu.stanford.nlp.ling.IndexedWord head = incoming.getGovernor();
            CoreLabel headSubject = findDependent(graph, head, "nsubj");
            if (headSubject != null) {
                return headSubject;
            }
            CoreLabel headPassive = findDependent(graph, head, "nsubj:pass");
            if (headPassive != null) {
                return headPassive;
            }
        }
        return null;
    }

    private String resolveVerbPredicate(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord verbNode) {
        String base = verbNode.lemma() == null
                ? normalizeToken(verbNode.word())
                : normalizeToken(verbNode.lemma());
        if (base.isEmpty()) {
            return "";
        }
        String particle = null;
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(verbNode)) {
            if ("compound".equals(edge.getRelation().getShortName())
                    && "prt".equals(edge.getRelation().getSpecific())) {
                particle = normalizeToken(edge.getDependent().word());
                break;
            }
        }
        if (particle == null || particle.isEmpty()) {
            return base;
        }
        return base + "_" + particle;
    }

    private String resolveVerbPredicate(SemanticGraph graph, CoreLabel verbLabel) {
        if (verbLabel == null) {
            return "";
        }
        var node = graph.getNodeByIndexSafe(verbLabel.index());
        if (node != null) {
            return resolveVerbPredicate(graph, node);
        }
        if (verbLabel.lemma() != null) {
            return normalizeToken(verbLabel.lemma());
        }
        return normalizeToken(verbLabel.word());
    }

    private String composeCompoundToken(SemanticGraph graph, CoreLabel head) {
        if (head == null) {
            return "";
        }
        var node = graph.getNodeByIndexSafe(head.index());
        if (node == null) {
            return head.word();
        }
        java.util.List<CoreLabel> parts = new java.util.ArrayList<>();
        parts.add(head);
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(node)) {
            String relation = edge.getRelation().getShortName();
            if (!"compound".equals(relation) && !"amod".equals(relation)) {
                continue;
            }
            if ("compound".equals(relation)
                    && (edge.getRelation().getSpecific() != null && !edge.getRelation().getSpecific().isBlank())) {
                continue;
            }
            parts.add(edge.getDependent().backingLabel());
        }
        if (parts.size() == 1) {
            return head.word();
        }
        parts.sort(java.util.Comparator.comparingInt(CoreLabel::index));
        java.util.List<String> words = new java.util.ArrayList<>(parts.size());
        for (CoreLabel part : parts) {
            words.add(part.word());
        }
        return String.join(" ", words);
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

    private boolean hasDependent(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor, String relation) {
        return findDependent(graph, governor, relation) != null;
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
        if (ontologyDriven) {
            if ("of".equals(prep) && isPartGovernor(governorWord)) {
                return "partOf";
            }
            if ("by".equals(prep) && governorWord != null && !governorWord.isBlank()) {
                return governorWord.toLowerCase(Locale.ROOT) + "By";
            }
            return prep;
        }
        if ("in".equals(prep)) {
            return PREDICATE_IN;
        }
        if ("at".equals(prep)) {
            return PREDICATE_AT;
        }
        if ("near".equals(prep)
                || "beside".equals(prep)
                || "alongside".equals(prep)
                || "next".equals(prep)
                || "next_to".equals(prep)
                || "next-to".equals(prep)) {
            return "with";
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

    private void addStatements(java.util.Map<String, Statement> target, java.util.List<Statement> statements) {
        for (Statement statement : statements) {
            String key = statement.subject().value() + "|" + statement.predicate() + "|" + statement.object().value();
            target.putIfAbsent(key, statement);
        }
    }

    private final class Helper implements StatementParserHelpers {
        @Override
        public Statement buildStatement(String subjectToken, String objectToken, String predicate, boolean objectIsConcept) {
            return StatementParser.this.buildStatement(subjectToken, objectToken, predicate, objectIsConcept);
        }

        @Override
        public CoreLabel findDependent(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor, String relation) {
            return StatementParser.this.findDependent(graph, governor, relation);
        }

        @Override
        public String normalizeCompoundToken(SemanticGraph graph, CoreLabel head) {
            return StatementParser.this.normalizeToken(composeCompoundToken(graph, head));
        }

        @Override
        public String normalizeToken(String raw) {
            return StatementParser.this.normalizeToken(raw);
        }

        @Override
        public String normalizeVerb(CoreLabel verbLabel) {
            if (verbLabel == null) {
                return "";
            }
            if (verbLabel.lemma() != null && !verbLabel.lemma().isBlank()) {
                return StatementParser.this.normalizeToken(verbLabel.lemma());
            }
            return StatementParser.this.normalizeToken(verbLabel.word());
        }
    }

    private Statement mergeStatements(Statement left, Statement right) {
        java.util.Map<String, Statement> merged = new java.util.LinkedHashMap<>();
        addStatements(merged, flattenStatements(left));
        addStatements(merged, flattenStatements(right));
        java.util.List<Statement> all = new java.util.ArrayList<>(merged.values());
        Statement primary = selectPrimaryStatement(all);
        java.util.List<Statement> extras = new java.util.ArrayList<>(all);
        extras.remove(primary);
        if (extras.isEmpty()) {
            return primary;
        }
        return new Statement(
                primary.subject(),
                primary.object(),
                primary.predicate(),
                primary.subjectTypes(),
                primary.objectTypes(),
                primary.objectIsConcept(),
                primary.confidence(),
                extras
        );
    }

    private java.util.List<Statement> flattenStatements(Statement statement) {
        java.util.List<Statement> all = new java.util.ArrayList<>();
        all.add(statement);
        all.addAll(statement.additionalStatements());
        return all;
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
        String normalized = trimmed.trim().replaceAll("\\s+", "_");
        return correctCommonTypos(normalized);
    }

    private String correctCommonTypos(String token) {
        if ("able".equals(token)) {
            return "table";
        }
        return token;
    }

    private static StanfordCoreNLP buildPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("ssplit.isOneSentence", "true");
        return new StanfordCoreNLP(props);
    }
}
