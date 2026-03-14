package com.sahr.nlp;

import com.sahr.core.QueryGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import edu.stanford.nlp.process.Morphology;

public final class LanguageRuleExecutor {
    private static final double DEFAULT_SCORE = 0.35;
    private static final Logger logger = Logger.getLogger(LanguageRuleExecutor.class.getName());
    private static final Morphology MORPHOLOGY = new Morphology();

    private final boolean ontologyDriven;

    public LanguageRuleExecutor(boolean ontologyDriven) {
        this.ontologyDriven = ontologyDriven;
    }

    public List<LanguageQueryCandidate> interpret(LanguageGraph graph) {
        if (graph == null) {
            return List.of();
        }

        LanguageGraph.QuestionShape shape = graph.questionShape();
        if (shape != LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING
                && shape != LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING
                && shape != LanguageGraph.QuestionShape.WH_VERB_OBJECT
                && shape != LanguageGraph.QuestionShape.WH_OBJECT_VERB) {
            return List.of();
        }

        String wh = graph.whToken();
        String relation = graph.relationToken();
        String anchor = graph.anchorToken();
        if (!isSupportedWh(wh)) {
            return List.of();
        }
        if (relation == null || anchor == null || anchor.isBlank()) {
            return List.of();
        }

        String predicate = shape == LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING
                || shape == LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING
                ? mapPrepositionPredicate(relation)
                : normalizeVerb(relation);
        if (predicate == null || predicate.isBlank()) {
            return List.of();
        }

        String expectedType = expectedTypeForWh(wh);
        boolean trailing = shape == LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING;
        boolean verbObject = shape == LanguageGraph.QuestionShape.WH_VERB_OBJECT;
        boolean verbSubject = shape == LanguageGraph.QuestionShape.WH_OBJECT_VERB;

        String subject = (trailing || verbSubject) ? anchor : null;
        String object = (trailing || verbSubject) ? null : anchor;

        QueryGoal goal = QueryGoal.relationWithModifier(subject, predicate, object, expectedType, graph.anchorModifier());
        String baseProducedBy = (shape == LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING
                || shape == LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING)
                ? "language-graph-preposition"
                : "language-graph-verb";
        LanguageQueryCandidate primary = new LanguageQueryCandidate(goal, DEFAULT_SCORE, baseProducedBy + "-primary");
        List<LanguageQueryCandidate> candidates = new ArrayList<>();
        candidates.add(primary);

        if (shouldEmitAlternate(shape, predicate, subject, object)) {
            QueryGoal alternateGoal = QueryGoal.relationWithModifier(object, predicate, subject, expectedType, graph.anchorModifier());
            double alternateScore = Math.max(0.0, DEFAULT_SCORE - 0.1);
            candidates.add(new LanguageQueryCandidate(alternateGoal, alternateScore, baseProducedBy + "-alternate"));
        }

        if (diagnosticsEnabled()) {
            logger.info(() -> "LanguageRuleExecutor candidates shape=" + shape
                    + " wh=" + wh
                    + " relation=" + relation
                    + " predicate=" + predicate
                    + " anchor=" + anchor
                    + " subject=" + subject
                    + " object=" + object
                    + " expectedType=" + expectedType
                    + " count=" + candidates.size()
                    + " utterance=\"" + graph.utterance() + "\"");
        }
        return candidates;
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

    private boolean isSupportedWh(String wh) {
        return "who".equals(wh) || "what".equals(wh);
    }

    private String mapPrepositionPredicate(String prep) {
        if (ontologyDriven) {
            return prep;
        }
        if ("in".equals(prep)) {
            return "locatedIn";
        }
        return prep;
    }

    private String normalizeVerb(String verb) {
        if (verb == null || verb.isBlank()) {
            return verb;
        }
        String lemma = MORPHOLOGY.lemma(verb, "VB");
        if (lemma != null && !lemma.isBlank()) {
            return lemma.toLowerCase(java.util.Locale.ROOT);
        }
        return verb.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean shouldEmitAlternate(LanguageGraph.QuestionShape shape,
                                        String predicate,
                                        String subject,
                                        String object) {
        if (shape != LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING || !"with".equals(predicate)) {
            return false;
        }
        return subject == null || subject.isBlank() || object == null || object.isBlank();
    }

    private boolean diagnosticsEnabled() {
        return Boolean.getBoolean("sahr.diagnostic.full")
                || Boolean.getBoolean("sahr.diagnostic.repl")
                || Boolean.parseBoolean(System.getenv().getOrDefault("SAHR_DIAGNOSTIC_FULL", "false"))
                || Boolean.parseBoolean(System.getenv().getOrDefault("SAHR_DIAGNOSTIC_REPL", "false"));
    }
}
