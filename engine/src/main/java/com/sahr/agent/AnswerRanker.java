package com.sahr.agent;

import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.QueryGoal;
import com.sahr.ontology.SahrAnnotationVocabulary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AnswerRanker {
    private final OntologyAnnotationResolver resolver;
    private final java.util.Map<String, Double> specificityCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Boolean> genericLossCache = new java.util.concurrent.ConcurrentHashMap<>();

    AnswerRanker(OntologyAnnotationResolver resolver) {
        this.resolver = resolver;
    }

    List<String> rankAnswerValues(List<String> values) {
        if (values == null || values.size() <= 1) {
            return values == null ? List.of() : values;
        }
        List<String> ranked = new ArrayList<>(values);
        ranked.sort((left, right) -> Double.compare(specificityScore(right), specificityScore(left)));
        return ranked;
    }

    double specificityScore(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        Double cached = specificityCache.get(value);
        if (cached != null) {
            return cached;
        }
        double score = 0.0;
        if (value.startsWith("entity:")) {
            score += 1.0;
        } else if (value.startsWith("concept:")) {
            score += 0.5;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = normalized.replace("entity:", "").replace("concept:", "");
        normalized = normalized.replaceAll("[^a-z0-9_ ]", "");
        String[] tokens = normalized.split("[_\\s]+");
        score += Math.min(0.4, tokens.length * 0.05);
        for (String token : tokens) {
            if (isContainerToken(token)) {
                score -= 0.35;
            }
            if (isLossToken(token)) {
                score -= 0.3;
            }
        }
        specificityCache.put(value, score);
        return score;
    }

    double assertionSpecificity(RelationAssertion assertion) {
        if (assertion == null) {
            return 0.0;
        }
        return Math.max(specificityScore(assertion.subject().value()),
                specificityScore(assertion.object().value()));
    }

    double assertionExplanationScore(RelationAssertion assertion, String predicateLocal) {
        if (assertion == null) {
            return 0.0;
        }
        return assertionSpecificity(assertion)
                + predicateDynamismScore(predicateLocal);
    }

    double ruleSpecificity(RuleAssertion rule) {
        if (rule == null) {
            return 0.0;
        }
        RelationAssertion consequent = rule.consequent();
        if (consequent == null) {
            return 0.0;
        }
        return Math.max(specificityScore(consequent.subject().value()),
                specificityScore(consequent.object().value()));
    }

    double ruleExplanationScore(RuleAssertion rule, String predicateLocal) {
        if (rule == null) {
            return 0.0;
        }
        RelationAssertion consequent = rule.consequent();
        if (consequent == null) {
            return 0.0;
        }
        return ruleSpecificity(rule)
                + predicateDynamismScore(predicateLocal);
    }

    double explanationSpecificity(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        for (String sentence : sentences) {
            if (sentence == null) {
                continue;
            }
            score += specificityScore(sentence);
            score += sentenceDynamismScore(sentence);
        }
        return score;
    }

    double predicateDynamismScore(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return 0.0;
        }
        return resolver.resolveObjectPropertyIri(predicate)
                .flatMap(iri -> resolver.annotationDouble(iri, SahrAnnotationVocabulary.DYNAMIC_WEIGHT))
                .orElse(0.0);
    }

    double sentenceDynamismScore(String sentence) {
        return 0.0;
    }

    ReasoningCandidate selectBestAnswerCandidate(List<ReasoningCandidate> candidates) {
        ReasoningCandidate winner = candidates.get(0);
        double winnerScore = candidateScore(winner);
        for (ReasoningCandidate candidate : candidates) {
            double score = candidateScore(candidate);
            if (score > winnerScore) {
                winner = candidate;
                winnerScore = score;
            }
        }
        return winner;
    }

    double candidateScore(ReasoningCandidate candidate) {
        if (candidate == null) {
            return 0.0;
        }
        double base = candidate.score();
        Object payload = candidate.payload();
        if (payload instanceof SymbolId) {
            base += specificityScore(((SymbolId) payload).value()) * 0.05;
        } else if (payload instanceof String) {
            base += specificityScore(payload.toString()) * 0.05;
        }
        return base;
    }

    List<ReasoningCandidate> filterEchoAnswers(List<ReasoningCandidate> answers, QueryGoal query) {
        if (answers == null || answers.isEmpty() || query == null) {
            return answers == null ? List.of() : answers;
        }
        List<ReasoningCandidate> filtered = new ArrayList<>();
        String subject = query.subject();
        String object = query.object();
        for (ReasoningCandidate candidate : answers) {
            if (candidate == null) {
                continue;
            }
            Object payload = candidate.payload();
            if (!(payload instanceof SymbolId)) {
                filtered.add(candidate);
                continue;
            }
            String value = ((SymbolId) payload).value();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.equals(subject) || value.equals(object)) {
                continue;
            }
            filtered.add(candidate);
        }
        return filtered.isEmpty() ? answers : filtered;
    }

    List<String> filterEchoValues(List<String> values, QueryGoal query) {
        if (values == null || values.isEmpty() || query == null) {
            return values == null ? List.of() : values;
        }
        List<String> filtered = new ArrayList<>();
        String subject = query.subject();
        String object = query.object();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.equals(subject) || value.equals(object)) {
                continue;
            }
            filtered.add(value);
        }
        return filtered.isEmpty() ? values : filtered;
    }

    boolean isGenericLossValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Boolean cached = genericLossCache.get(value);
        if (cached != null) {
            return cached;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = normalized.replace("entity:", "").replace("concept:", "");
        normalized = normalized.replaceAll("[^a-z0-9_ ]", "");
        String[] tokens = normalized.split("[_\\s]+");
        for (String token : tokens) {
            if (isLossToken(token)) {
                genericLossCache.put(value, true);
                return true;
            }
        }
        genericLossCache.put(value, false);
        return false;
    }

    private boolean isContainerToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return switch (token) {
            case "system", "mechanism", "device", "entity",
                    "object", "thing", "relationship", "process", "event",
                    "information", "data", "component" -> true;
            default -> false;
        };
    }

    private boolean isLossToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return "loss".equals(token) || token.startsWith("loss");
    }
}
