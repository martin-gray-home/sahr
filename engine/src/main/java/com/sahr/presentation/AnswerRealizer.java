package com.sahr.presentation;

import com.sahr.core.QueryBinding;
import com.sahr.core.QueryGoal;
import com.sahr.core.QueryResult;
import com.sahr.core.RelationAssertion;

import java.util.function.Function;

public final class AnswerRealizer {
    private final Function<String, String> localNameResolver;

    public AnswerRealizer(Function<String, String> localNameResolver) {
        this.localNameResolver = localNameResolver;
    }

    public String formatQueryResultAnswer(QueryGoal query, QueryResult result) {
        if (result == null) {
            return "No candidates produced.";
        }
        return switch (result.operator()) {
            case COUNT -> String.valueOf(result.count());
            case EXISTS -> formatYesNoResult(query, result);
            default -> result.bindings().isEmpty()
                    ? formatFactResult(result)
                    : result.bindings().get(0).answer().value();
        };
    }

    private String formatYesNoResult(QueryGoal query, QueryResult result) {
        if (result == null || !result.exists() || result.bindings().isEmpty()) {
            return "No.";
        }
        QueryBinding binding = result.bindings().get(0);
        return formatYesNoAnswer(query, binding);
    }

    private String formatYesNoAnswer(QueryGoal query, QueryBinding binding) {
        String subjectText = query.subjectText() != null ? query.subjectText() : binding.subject().toString();
        String objectText = query.objectText() != null ? query.objectText() : binding.object().toString();
        String predicateText = query.predicateText() != null ? query.predicateText() : binding.predicate();
        if (binding.matchType() == com.sahr.core.PredicateMatchType.INVERSE) {
            subjectText = query.subjectText() != null ? query.subjectText() : binding.answer().value();
            objectText = query.objectText() != null ? query.objectText() : binding.object().value();
            predicateText = query.predicateText() != null ? query.predicateText() : query.predicate();
        }
        predicateText = normalizePredicateText(predicateText);
        return "Yes, " + subjectText + " " + predicateText + " " + objectText;
    }

    private String formatFactResult(QueryResult result) {
        if (result == null || result.facts().isEmpty()) {
            return "No candidates produced.";
        }
        RelationAssertion fact = result.facts().get(0);
        return formatFactTriple(fact);
    }

    private String formatFactTriple(RelationAssertion fact) {
        if (fact == null) {
            return "No candidates produced.";
        }
        String predicate = localNameResolver.apply(fact.predicate());
        if (predicate == null || predicate.isBlank()) {
            predicate = fact.predicate();
        }
        if ("inside".equals(predicate) || "locatedin".equals(predicate)) {
            predicate = "in";
        }
        return fact.subject().value() + " " + predicate + " " + fact.object().value();
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
