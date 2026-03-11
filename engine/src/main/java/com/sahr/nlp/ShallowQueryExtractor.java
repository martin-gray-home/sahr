package com.sahr.nlp;

import com.sahr.core.QueryGoal;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import java.util.Locale;
import java.util.Optional;

public final class ShallowQueryExtractor {
    private static final String USE_CORENLP_PROP = "sahr.queryProposer.useCoreNlp";
    private ShallowQueryExtractor() {
    }

    public static Optional<QueryGoal> propose(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        if (useCoreNlp()) {
            Annotation doc = new Annotation(input);
            CoreNlpPipeline.get().annotate(doc);
            for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
                SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
                if (graph == null) {
                    continue;
                }
                Optional<QueryGoal> fromGraph = proposeFromGraph(graph);
                if (fromGraph.isPresent()) {
                    return fromGraph;
                }
            }
            return Optional.empty();
        }
        return proposeFromTokens(input);
    }

    private static Optional<QueryGoal> proposeFromGraph(SemanticGraph graph) {
        CoreLabel whNoun = null;
        String whToken = null;
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"det".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel det = edge.getDependent().backingLabel();
            String detToken = det.lemma().toLowerCase(Locale.ROOT);
            if ("what".equals(detToken) || "which".equals(detToken)) {
                whNoun = edge.getGovernor().backingLabel();
                whToken = detToken;
                break;
            }
        }
        if (whToken == null) {
            for (SemanticGraphEdge edge : graph.edgeIterable()) {
                if (!"nsubj".equals(edge.getRelation().getShortName())) {
                    continue;
                }
                CoreLabel subj = edge.getDependent().backingLabel();
                String lemma = subj.lemma().toLowerCase(Locale.ROOT);
                if ("who".equals(lemma) || "what".equals(lemma) || "which".equals(lemma) || "why".equals(lemma)) {
                    whToken = lemma;
                    whNoun = subj;
                    break;
                }
            }
        }

        CoreLabel rootVerb = graph.getFirstRoot() == null ? null : graph.getFirstRoot().backingLabel();
        if (rootVerb == null) {
            return Optional.empty();
        }
        String predicate = normalizeVerb(rootVerb.lemma());
        if ("why".equals(whToken)) {
            CoreLabel object = findDependent(graph, rootVerb, "obj");
            CoreLabel subject = findDependent(graph, rootVerb, "nsubj");
            String objectToken = normalizeToken(object != null ? object.lemma() : null);
            if (objectToken == null || objectToken.isBlank()) {
                objectToken = normalizeToken(subject != null ? subject.lemma() : null);
            }
            if (objectToken == null || objectToken.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(QueryGoal.relation(null, "cause", objectToken, null));
        }

        CoreLabel subject = findDependent(graph, rootVerb, "nsubj");
        CoreLabel object = findDependent(graph, rootVerb, "obj");
        String subjectToken = normalizeToken(subject != null ? subject.lemma() : null);
        String objectToken = normalizeToken(object != null ? object.lemma() : null);
        String expectedType = normalizeToken(whNoun != null ? whNoun.lemma() : null);

        if (whNoun != null) {
            if (isSameNode(whNoun, subject)) {
                return Optional.of(QueryGoal.relation(null, predicate, objectToken, expectedType));
            }
            if (isSameNode(whNoun, object)) {
                return Optional.of(QueryGoal.relation(subjectToken, predicate, null, expectedType));
            }
        }
        return Optional.of(QueryGoal.relation(null, predicate, null, expectedType));
    }

    private static Optional<QueryGoal> proposeFromTokens(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("\\s+");
        String wh = null;
        for (String token : tokens) {
            if (token.startsWith("what") || token.startsWith("which") || token.startsWith("who")
                    || token.startsWith("why") || token.startsWith("how")) {
                wh = token.replaceAll("[^a-z]", "");
                break;
            }
        }
        if (wh == null && !lower.contains("?")) {
            return Optional.empty();
        }
        if ("why".equals(wh) || lower.startsWith("explain")) {
            String objectToken = lastContentToken(tokens);
            if (objectToken == null) {
                return Optional.empty();
            }
            return Optional.of(QueryGoal.relation(null, "cause", objectToken, null));
        }
        String predicate = lastVerbLike(tokens);
        if (predicate == null) {
            return Optional.empty();
        }
        String expectedType = nextTokenAfter(tokens, wh);
        return Optional.of(QueryGoal.relation(null, predicate, null, expectedType));
    }

    private static String lastContentToken(String[] tokens) {
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = normalizeToken(tokens[i]);
            if (!token.isEmpty() && !"question".equals(token)) {
                return token;
            }
        }
        return null;
    }

    private static String lastVerbLike(String[] tokens) {
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = normalizeToken(tokens[i]);
            if (token.isEmpty()) {
                continue;
            }
            if (token.endsWith("ed") || token.endsWith("ing") || token.endsWith("s")) {
                return normalizeVerb(token);
            }
        }
        return null;
    }

    private static String nextTokenAfter(String[] tokens, String marker) {
        if (marker == null) {
            return null;
        }
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].startsWith(marker)) {
                String next = normalizeToken(tokens[i + 1]);
                return next.isEmpty() ? null : next;
            }
        }
        return null;
    }

    private static boolean useCoreNlp() {
        String value = System.getProperty(USE_CORENLP_PROP, "false");
        return "true".equalsIgnoreCase(value);
    }

    private static CoreLabel findDependent(SemanticGraph graph, CoreLabel governor, String relation) {
        if (graph == null || governor == null) {
            return null;
        }
        var node = graph.getNodeByIndexSafe(governor.index());
        if (node == null) {
            return null;
        }
        for (SemanticGraphEdge edge : graph.outgoingEdgeList(node)) {
            if (relation.equals(edge.getRelation().getShortName())) {
                return edge.getDependent().backingLabel();
            }
        }
        return null;
    }

    private static boolean isSameNode(CoreLabel left, CoreLabel right) {
        if (left == null || right == null) {
            return false;
        }
        return left.index() == right.index();
    }

    private static String normalizeVerb(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if ("stop".equals(value)) {
            return "fail";
        }
        return value;
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z_]", "");
    }

    private static edu.stanford.nlp.pipeline.StanfordCoreNLP buildPipeline() {
        return CoreNlpPipeline.get();
    }
}
