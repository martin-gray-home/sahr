package com.sahr.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SymbolicWorkingSetUsageAnnotator {
    public SymbolicWorkingSet annotate(SymbolicWorkingSet workingSet, ReasoningCandidate winner) {
        if (workingSet == null || winner == null) {
            return workingSet;
        }

        Map<String, LinkedHashSet<String>> assertionReasons = new LinkedHashMap<>();
        Map<RuleFrame, LinkedHashSet<String>> ruleReasons = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> ruleTextReasons = new LinkedHashMap<>();
        Map<SymbolId, LinkedHashSet<String>> entityReasons = new LinkedHashMap<>();

        collectFromPayload(winner.payload(), entityReasons, assertionReasons, ruleReasons, ruleTextReasons);
        collectFromEvidence(winner.evidence(), entityReasons, assertionReasons, ruleReasons, ruleTextReasons);

        List<SymbolicWorkingSet.IncludedEntity> entities = new ArrayList<>(workingSet.entities().size());
        for (SymbolicWorkingSet.IncludedEntity included : workingSet.entities()) {
            List<String> reasons = List.copyOf(entityReasons.getOrDefault(included.entity(), new LinkedHashSet<>()));
            entities.add(reasons.isEmpty() ? included : included.withWinnerUsage(reasons));
        }

        List<SymbolicWorkingSet.IncludedAssertion> assertions = new ArrayList<>(workingSet.assertions().size());
        for (SymbolicWorkingSet.IncludedAssertion included : workingSet.assertions()) {
            String key = FocusedKnowledgeBase.assertionKey(
                    included.assertion().subject(),
                    included.assertion().predicate(),
                    included.assertion().object()
            );
            List<String> reasons = List.copyOf(assertionReasons.getOrDefault(key, new LinkedHashSet<>()));
            assertions.add(reasons.isEmpty() ? included : included.withWinnerUsage(reasons));
        }

        List<SymbolicWorkingSet.IncludedRule> rules = new ArrayList<>(workingSet.rules().size());
        for (SymbolicWorkingSet.IncludedRule included : workingSet.rules()) {
            LinkedHashSet<String> mergedReasons = new LinkedHashSet<>();
            mergedReasons.addAll(ruleReasons.getOrDefault(included.rule(), new LinkedHashSet<>()));
            mergedReasons.addAll(ruleTextReasons.getOrDefault(included.rule().toString(), new LinkedHashSet<>()));
            List<String> reasons = List.copyOf(mergedReasons);
            rules.add(reasons.isEmpty() ? included : included.withWinnerUsage(reasons));
        }

        return new SymbolicWorkingSet(workingSet.view(), workingSet.reduced(), entities, assertions, rules);
    }

    private void collectFromPayload(Object payload,
                                    Map<SymbolId, LinkedHashSet<String>> entityReasons,
                                    Map<String, LinkedHashSet<String>> assertionReasons,
                                    Map<RuleFrame, LinkedHashSet<String>> ruleReasons,
                                    Map<String, LinkedHashSet<String>> ruleTextReasons) {
        if (payload instanceof SymbolId symbolId) {
            addEntityReason(entityReasons, symbolId, "winner.payload.symbol");
        }
        if (payload instanceof RelationAssertion assertion) {
            addAssertion(assertion, "winner.payload.assertion", entityReasons, assertionReasons);
        }
        if (payload instanceof QueryResult result) {
            for (RelationAssertion fact : result.facts()) {
                addAssertion(fact, "winner.payload.query_result_fact", entityReasons, assertionReasons);
            }
            for (QueryBinding binding : result.bindings()) {
                if (binding.subject() != null) {
                    addEntityReason(entityReasons, binding.subject(), "winner.payload.query_result_binding");
                }
                if (binding.object() != null) {
                    addEntityReason(entityReasons, binding.object(), "winner.payload.query_result_binding");
                }
                if (binding.answer() != null) {
                    addEntityReason(entityReasons, binding.answer(), "winner.payload.query_result_answer");
                }
                if (binding.evidence() != null && !binding.evidence().isBlank()) {
                    parseEvidence(binding.evidence(), entityReasons, assertionReasons, ruleReasons, ruleTextReasons, "winner.binding.evidence");
                }
            }
        }
        if (payload instanceof RuleFrame ruleFrame) {
            addRuleReason(ruleReasons, ruleFrame, "winner.payload.rule");
            collectEntitiesFromRule(ruleFrame, entityReasons, "winner.payload.rule");
        }
        if (payload instanceof QueryGoal goal) {
            collectEntitiesFromGoal(goal, entityReasons, "winner.payload.query_goal");
        }
        if (payload instanceof QueryPlan plan) {
            collectEntitiesFromGoal(plan.goal(), entityReasons, "winner.payload.query_plan");
        }
    }

    private void collectFromEvidence(List<String> evidence,
                                     Map<SymbolId, LinkedHashSet<String>> entityReasons,
                                     Map<String, LinkedHashSet<String>> assertionReasons,
                                     Map<RuleFrame, LinkedHashSet<String>> ruleReasons,
                                     Map<String, LinkedHashSet<String>> ruleTextReasons) {
        if (evidence == null) {
            return;
        }
        for (String item : evidence) {
            parseEvidence(item, entityReasons, assertionReasons, ruleReasons, ruleTextReasons, "winner.evidence");
        }
    }

    private void parseEvidence(String item,
                               Map<SymbolId, LinkedHashSet<String>> entityReasons,
                               Map<String, LinkedHashSet<String>> assertionReasons,
                               Map<RuleFrame, LinkedHashSet<String>> ruleReasons,
                               Map<String, LinkedHashSet<String>> ruleTextReasons,
                               String reasonPrefix) {
        if (item == null || item.isBlank()) {
            return;
        }
        String trimmed = item.trim();
        if (trimmed.startsWith("rule(")) {
            ruleTextReasons.computeIfAbsent(trimmed, ignored -> new LinkedHashSet<>()).add(reasonPrefix + ".rule_text");
            return;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return;
        }
        SymbolId subject = new SymbolId(parts[0]);
        String predicate = parts[1];
        SymbolId object = new SymbolId(parts[2]);
        addAssertion(new RelationAssertion(subject, predicate, object, 1.0), reasonPrefix + ".assertion_text", entityReasons, assertionReasons);
    }

    private void addAssertion(RelationAssertion assertion,
                              String reason,
                              Map<SymbolId, LinkedHashSet<String>> entityReasons,
                              Map<String, LinkedHashSet<String>> assertionReasons) {
        String key = FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
        assertionReasons.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(reason);
        addEntityReason(entityReasons, assertion.subject(), reason + ".subject");
        addEntityReason(entityReasons, assertion.object(), reason + ".object");
    }

    private void collectEntitiesFromRule(RuleFrame rule,
                                         Map<SymbolId, LinkedHashSet<String>> entityReasons,
                                         String reason) {
        for (RuleAtom atom : rule.antecedents()) {
            addRuleTerm(atom.subject(), entityReasons, reason);
            addRuleTerm(atom.object(), entityReasons, reason);
        }
        addRuleTerm(rule.consequent().subject(), entityReasons, reason);
        addRuleTerm(rule.consequent().object(), entityReasons, reason);
    }

    private void collectEntitiesFromGoal(QueryGoal goal,
                                         Map<SymbolId, LinkedHashSet<String>> entityReasons,
                                         String reason) {
        if (goal == null) {
            return;
        }
        if (goal.subject() != null && !goal.subject().isBlank()) {
            addEntityReason(entityReasons, new SymbolId(goal.subject()), reason + ".subject");
        }
        if (goal.object() != null && !goal.object().isBlank()) {
            addEntityReason(entityReasons, new SymbolId(goal.object()), reason + ".object");
        }
    }

    private void addRuleTerm(RuleTerm term,
                             Map<SymbolId, LinkedHashSet<String>> entityReasons,
                             String reason) {
        if (term == null || term.isVariable()) {
            return;
        }
        addEntityReason(entityReasons, new SymbolId(term.value()), reason + ".rule_constant");
    }

    private void addRuleReason(Map<RuleFrame, LinkedHashSet<String>> ruleReasons, RuleFrame rule, String reason) {
        ruleReasons.computeIfAbsent(rule, ignored -> new LinkedHashSet<>()).add(reason);
    }

    private void addEntityReason(Map<SymbolId, LinkedHashSet<String>> entityReasons, SymbolId entity, String reason) {
        entityReasons.computeIfAbsent(entity, ignored -> new LinkedHashSet<>()).add(reason);
    }
}
