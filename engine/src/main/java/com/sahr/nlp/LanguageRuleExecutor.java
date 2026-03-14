package com.sahr.nlp;

import com.sahr.core.QueryGoal;

import java.util.Optional;

public final class LanguageRuleExecutor {
    private static final double DEFAULT_SCORE = 0.35;

    private final boolean ontologyDriven;

    public LanguageRuleExecutor(boolean ontologyDriven) {
        this.ontologyDriven = ontologyDriven;
    }

    public Optional<LanguageQueryCandidate> interpret(LanguageGraph graph) {
        if (graph == null || graph.questionShape() != LanguageGraph.QuestionShape.WH_PREPOSITION) {
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
        String subject = "with".equals(predicate) ? anchor : null;
        String object = "with".equals(predicate) ? null : anchor;

        QueryGoal goal = QueryGoal.relationWithModifier(subject, predicate, object, expectedType, graph.anchorModifier());
        return Optional.of(new LanguageQueryCandidate(goal, DEFAULT_SCORE, "language-graph-preposition"));
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
}
