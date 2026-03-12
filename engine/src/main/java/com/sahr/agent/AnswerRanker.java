package com.sahr.agent;

import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.core.QueryGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AnswerRanker {
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
        }
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
        return switch (predicate) {
            case "fail", "fails", "stop", "stops", "operate", "operates", "restore", "restores",
                    "regain", "regains", "fire", "fires", "fired", "respond", "responds", "drop",
                    "drops", "spike", "spikes", "cause", "causes", "causedby" -> 0.55;
            case "poweredby", "require", "requires", "contain", "contains", "type", "rdf:type" -> -0.25;
            default -> 0.0;
        };
    }

    double sentenceDynamismScore(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return 0.0;
        }
        String normalized = sentence.toLowerCase(Locale.ROOT);
        double score = 0.0;
        if (normalized.contains("fail") || normalized.contains("stop")
                || normalized.contains("restore") || normalized.contains("regain")
                || normalized.contains("fired") || normalized.contains("respond")
                || normalized.contains("drop") || normalized.contains("spike")) {
            score += 0.4;
        }
        if (normalized.contains("powered by") || normalized.contains("requires")
                || normalized.contains("contains")) {
            score -= 0.2;
        }
        return score;
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

    private boolean isContainerToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return switch (token) {
            case "spacecraft", "system", "mechanism", "device", "entity",
                    "object", "thing", "relationship", "process", "event",
                    "information", "data", "component" -> true;
            default -> false;
        };
    }
}
