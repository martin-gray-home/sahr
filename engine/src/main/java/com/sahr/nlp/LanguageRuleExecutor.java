package com.sahr.nlp;

import com.sahr.core.QueryGoal;

import java.util.Optional;
import java.util.logging.Logger;

public final class LanguageRuleExecutor {
    private static final double DEFAULT_SCORE = 0.35;
    private static final Logger logger = Logger.getLogger(LanguageRuleExecutor.class.getName());

    private final boolean ontologyDriven;

    public LanguageRuleExecutor(boolean ontologyDriven) {
        this.ontologyDriven = ontologyDriven;
    }

    public Optional<LanguageQueryCandidate> interpret(LanguageGraph graph) {
        if (graph == null) {
            return Optional.empty();
        }

        LanguageGraph.QuestionShape shape = graph.questionShape();
        if (shape != LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING
                && shape != LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING) {
            return Optional.empty();
        }

        String wh = graph.whToken();
        String relation = graph.relationToken();
        String anchor = graph.anchorToken();
        if (wh == null || relation == null || anchor == null || anchor.isBlank()) {
            return Optional.empty();
        }

        String predicate = mapPrepositionPredicate(relation);
        if (predicate == null || predicate.isBlank()) {
            return Optional.empty();
        }

        String expectedType = expectedTypeForWh(wh);
        boolean trailing = shape == LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING;
        String subject = trailing ? anchor : null;
        String object = trailing ? null : anchor;

        QueryGoal goal = QueryGoal.relationWithModifier(subject, predicate, object, expectedType, graph.anchorModifier());
        LanguageQueryCandidate candidate = new LanguageQueryCandidate(goal, DEFAULT_SCORE, "language-graph-preposition");
        if (diagnosticsEnabled()) {
            logger.info(() -> "LanguageRuleExecutor candidate shape=" + shape
                    + " wh=" + wh
                    + " relation=" + relation
                    + " predicate=" + predicate
                    + " anchor=" + anchor
                    + " subject=" + subject
                    + " object=" + object
                    + " expectedType=" + expectedType
                    + " utterance=\"" + graph.utterance() + "\"");
        }
        return Optional.of(candidate);
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

    private String mapPrepositionPredicate(String prep) {
        if (ontologyDriven) {
            return prep;
        }
        if ("in".equals(prep)) {
            return "locatedIn";
        }
        return prep;
    }

    private boolean diagnosticsEnabled() {
        return Boolean.getBoolean("sahr.diagnostic.full")
                || Boolean.getBoolean("sahr.diagnostic.repl")
                || Boolean.parseBoolean(System.getenv().getOrDefault("SAHR_DIAGNOSTIC_FULL", "false"))
                || Boolean.parseBoolean(System.getenv().getOrDefault("SAHR_DIAGNOSTIC_REPL", "false"));
    }
}
