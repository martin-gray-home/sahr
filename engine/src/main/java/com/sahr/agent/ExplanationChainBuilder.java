package com.sahr.agent;

import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

final class ExplanationChainBuilder {
    interface Formatter {
        String localName(String predicate);

        String formatAssertionSentence(RelationAssertion assertion);

        String formatRuleSentence(RuleAssertion rule);

        String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect);

        String normalizeTypeToken(String raw);
    }

    private final KnowledgeBase graph;
    @SuppressWarnings("unused")
    private final OntologyService ontology;
    private final Formatter formatter;
    private final ToDoubleFunction<String> specificityScore;

    ExplanationChainBuilder(KnowledgeBase graph,
                            OntologyService ontology,
                            Formatter formatter,
                            ToDoubleFunction<String> specificityScore) {
        this.graph = graph;
        this.ontology = ontology;
        this.formatter = formatter;
        this.specificityScore = specificityScore;
    }

    List<String> buildExplanationChain(SymbolId target, int maxDepth) {
        List<String> sentences = new ArrayList<>();
        if (target == null) {
            return sentences;
        }
        Set<String> seen = new HashSet<>();
        sentences.addAll(buildExplanationChainFrom(target, maxDepth, seen));
        if (sentences.isEmpty()) {
            sentences.addAll(collectTemporalEvidence(target, null, maxDepth, seen));
        }
        return sentences;
    }

    List<String> buildExplanationChainFrom(SymbolId start, int maxDepth, Set<String> seen) {
        List<String> sentences = new ArrayList<>();
        if (start == null) {
            return sentences;
        }
        Set<SymbolId> visited = new HashSet<>();
        SymbolId current = start;
        visited.add(current);
        for (int depth = 0; depth < maxDepth; depth++) {
            RelationAssertion causeAssertion = selectBestCauseAssertion(current);
            if (causeAssertion != null) {
                SymbolId cause = causeFromAssertion(causeAssertion, current);
                if (cause == null) {
                    break;
                }
                String sentence = formatter.formatCausalSentence(causeAssertion, cause, current);
                if (seen.add(sentence)) {
                    sentences.add(sentence);
                }
                sentences.addAll(collectTemporalEvidence(cause, current, maxDepth, seen));
                if (!visited.add(cause)) {
                    break;
                }
                current = cause;
                continue;
            }
            RuleAssertion rule = selectBestRuleForConsequent(current);
            if (rule != null) {
                String sentence = formatter.formatRuleSentence(rule);
                if (seen.add(sentence)) {
                    sentences.add(sentence);
                }
                SymbolId cause = selectCauseNode(rule.antecedent());
                if (cause == null || !visited.add(cause)) {
                    break;
                }
                sentences.addAll(collectTemporalEvidence(cause, current, maxDepth, seen));
                current = cause;
                continue;
            }
            break;
        }
        return sentences;
    }

    RelationAssertion selectBestCauseAssertion(SymbolId effect) {
        List<RelationAssertion> candidates = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"cause".equals(predicate) && !"causedby".equals(predicate)) {
                continue;
            }
            if ("causedby".equals(predicate)) {
                if (assertion.subject().equals(effect)) {
                    candidates.add(assertion);
                }
                continue;
            }
            if (assertion.object().equals(effect)) {
                candidates.add(assertion);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        RelationAssertion best = candidates.get(0);
        double bestScore = causeEvidenceScore(causeFromAssertion(best, effect), effect);
        for (RelationAssertion candidate : candidates) {
            SymbolId cause = causeFromAssertion(candidate, effect);
            if (cause == null) {
                continue;
            }
            double score = causeEvidenceScore(cause, effect);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    RuleAssertion selectBestRuleForConsequent(SymbolId effect) {
        List<RuleAssertion> candidates = new ArrayList<>();
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            if (consequent.subject().equals(effect) || consequent.object().equals(effect)) {
                candidates.add(rule);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        RuleAssertion best = candidates.get(0);
        SymbolId bestCause = selectCauseNode(best.antecedent());
        double bestScore = bestCause == null ? 0.0 : causeEvidenceScore(bestCause, effect);
        for (RuleAssertion candidate : candidates) {
            SymbolId cause = selectCauseNode(candidate.antecedent());
            double score = cause == null ? 0.0 : causeEvidenceScore(cause, effect);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    List<String> buildRecoveryEvidence(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"during".equals(predicate) && !"after".equals(predicate)) {
                continue;
            }
            String objectValue = assertion.object().value().toLowerCase(java.util.Locale.ROOT);
            if (!objectValue.contains("recovery")) {
                continue;
            }
            sentences.add(formatter.formatAssertionSentence(assertion));
            if (sentences.size() >= limit) {
                return sentences;
            }
        }
        return sentences;
    }

    private double causeEvidenceScore(SymbolId cause, SymbolId effect) {
        if (cause == null) {
            return 0.0;
        }
        double score = specificityScore.applyAsDouble(cause.value());
        score += temporalSupportScore(cause, effect);
        score += telemetrySupportScore(cause);
        return score;
    }

    private double temporalSupportScore(SymbolId cause, SymbolId effect) {
        if (cause == null || effect == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            if (assertion.subject().equals(cause) && assertion.object().equals(effect)) {
                score += 0.4;
            } else if ("after".equals(predicate) && assertion.subject().equals(effect) && assertion.object().equals(cause)) {
                score += 0.4;
            } else if ("before".equals(predicate) && assertion.subject().equals(effect) && assertion.object().equals(cause)) {
                score += 0.2;
            } else if ("during".equals(predicate)
                    && (assertion.subject().equals(cause) || assertion.subject().equals(effect))) {
                score += 0.2;
            }
        }
        return score;
    }

    private double telemetrySupportScore(SymbolId cause) {
        if (cause == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if ("indicate".equals(predicate) || "signal".equals(predicate) || "suggest".equals(predicate)) {
                if (assertion.object().equals(cause) || assertion.subject().equals(cause)) {
                    score += 0.35;
                }
            }
            if ("telemetry".equals(formatter.normalizeTypeToken(assertion.subject().value()))) {
                if (assertion.object().equals(cause)) {
                    score += 0.2;
                }
            }
        }
        return score;
    }

    private List<String> collectTemporalEvidence(SymbolId cause,
                                                 SymbolId effect,
                                                 int limit,
                                                 Set<String> seen) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            boolean matches = false;
            if (cause != null && effect != null) {
                matches = assertion.subject().equals(cause) && assertion.object().equals(effect);
                matches = matches || assertion.subject().equals(effect) && assertion.object().equals(cause);
            } else if (cause != null) {
                matches = assertion.subject().equals(cause);
            } else if (effect != null) {
                matches = assertion.subject().equals(effect);
            }
            if (!matches) {
                continue;
            }
            String sentence = formatter.formatAssertionSentence(assertion);
            if (seen.add(sentence)) {
                sentences.add(sentence);
            }
            if (sentences.size() >= limit) {
                return sentences;
            }
        }
        return sentences;
    }

    private SymbolId causeFromAssertion(RelationAssertion assertion, SymbolId effect) {
        if (assertion == null || effect == null) {
            return null;
        }
        String predicate = formatter.localName(assertion.predicate());
        if ("causedby".equals(predicate)) {
            return assertion.object();
        }
        return assertion.subject();
    }

    private SymbolId selectCauseNode(RelationAssertion assertion) {
        if (assertion == null) {
            return null;
        }
        SymbolId subject = assertion.subject();
        SymbolId object = assertion.object();
        if (subject != null && !"concept:true".equals(subject.value())) {
            return subject;
        }
        return object;
    }
}
