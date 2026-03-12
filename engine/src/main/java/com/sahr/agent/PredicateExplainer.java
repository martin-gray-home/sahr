package com.sahr.agent;

import com.sahr.core.KnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

final class PredicateExplainer {
    interface Formatter {
        String localName(String predicate);

        String formatAssertionSentence(RelationAssertion assertion);

        String formatRuleSentence(RuleAssertion rule);

        SymbolId selectCauseNode(RelationAssertion antecedent);
    }

    private final KnowledgeBase graph;
    private final Formatter formatter;
    private final ToDoubleFunction<RelationAssertion> assertionScore;
    private final ToDoubleFunction<RuleAssertion> ruleScore;
    private final ExplanationChainBuilder explanationChains;

    PredicateExplainer(KnowledgeBase graph,
                       Formatter formatter,
                       ToDoubleFunction<RelationAssertion> assertionScore,
                       ToDoubleFunction<RuleAssertion> ruleScore,
                       ExplanationChainBuilder explanationChains) {
        this.graph = graph;
        this.formatter = formatter;
        this.assertionScore = assertionScore;
        this.ruleScore = ruleScore;
        this.explanationChains = explanationChains;
    }

    List<String> buildPredicateExplanation(QueryGoal goal, String predicate, int limit) {
        List<String> sentences = new ArrayList<>();
        if (goal == null || predicate == null || predicate.isBlank()) {
            return sentences;
        }
        SymbolId subject = goal.subject() == null ? null : new SymbolId(goal.subject());
        SymbolId object = goal.object() == null ? null : new SymbolId(goal.object());
        List<RelationAssertion> assertionMatches = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!predicate.equals(formatter.localName(assertion.predicate()))) {
                continue;
            }
            if (subject != null && !assertion.subject().equals(subject)) {
                continue;
            }
            if (object != null && !assertion.object().equals(object)) {
                continue;
            }
            assertionMatches.add(assertion);
        }
        assertionMatches.sort((left, right) -> Double.compare(
                assertionScore.applyAsDouble(right),
                assertionScore.applyAsDouble(left)
        ));
        for (RelationAssertion assertion : assertionMatches) {
            sentences.add(formatter.formatAssertionSentence(assertion));
            if (sentences.size() >= limit) {
                return sentences;
            }
        }

        List<RuleAssertion> ruleMatches = new ArrayList<>();
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            if (!predicate.equals(formatter.localName(consequent.predicate()))) {
                continue;
            }
            if (subject != null && !consequent.subject().equals(subject)) {
                continue;
            }
            if (object != null && !consequent.object().equals(object)) {
                continue;
            }
            ruleMatches.add(rule);
        }
        ruleMatches.sort((left, right) -> Double.compare(
                ruleScore.applyAsDouble(right),
                ruleScore.applyAsDouble(left)
        ));
        Set<String> seen = new HashSet<>(sentences);
        for (RuleAssertion rule : ruleMatches) {
            String ruleSentence = formatter.formatRuleSentence(rule);
            if (seen.add(ruleSentence)) {
                sentences.add(ruleSentence);
            }
            if (sentences.size() >= limit) {
                return sentences;
            }
            SymbolId next = formatter.selectCauseNode(rule.antecedent());
            if (next == null) {
                continue;
            }
            List<String> followUp = explanationChains.buildExplanationChainFrom(
                    next,
                    Math.max(1, limit - sentences.size()),
                    seen
            );
            if (!followUp.isEmpty()) {
                sentences.addAll(followUp);
                if (sentences.size() >= limit) {
                    return sentences.subList(0, limit);
                }
            }
        }
        if (sentences.size() < limit && ("restore".equals(predicate) || "regain".equals(predicate))) {
            sentences.addAll(explanationChains.buildRecoveryEvidence(limit - sentences.size()));
        }
        return sentences;
    }
}
