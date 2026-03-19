package com.sahr.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SymbolicWorkingSetBuilder {
    private static final int MAX_ACTIVE_ENTITIES = 8;
    private static final int MAX_GOALS = 4;
    private static final int MAX_RECENT_ASSERTIONS = 12;
    private static final int MAX_ASSERTIONS = 48;
    private static final int MAX_RULES = 24;

    public KnowledgeBase build(HeadContext context, KnowledgeBase graph) {
        return buildWorkingSet(context, graph).view();
    }

    public SymbolicWorkingSet buildWorkingSet(HeadContext context, KnowledgeBase graph) {
        if (graph == null) {
            throw new IllegalArgumentException("graph must not be null");
        }
        if (context == null) {
            return new SymbolicWorkingSet(graph, false, List.of(), List.of(), List.of());
        }

        Set<SymbolId> seedEntities = new LinkedHashSet<>();
        Set<String> seedPredicates = new LinkedHashSet<>();
        Set<String> assertionKeys = new LinkedHashSet<>();
        Set<RuleFrame> ruleFrames = new LinkedHashSet<>();
        Map<SymbolId, LinkedHashSet<String>> entityReasons = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> assertionReasons = new LinkedHashMap<>();
        Map<RuleFrame, LinkedHashSet<String>> ruleReasons = new LinkedHashMap<>();
        WorkingMemory memory = context.workingMemory();

        collectQuerySeeds(context.query(), seedEntities, seedPredicates, entityReasons);
        collectWorkingMemorySeeds(memory, seedEntities, seedPredicates, assertionKeys, entityReasons, assertionReasons);

        for (SymbolId entity : new ArrayList<>(seedEntities)) {
            addAssertions(assertionKeys, assertionReasons, graph.findBySubject(entity), "entity_seed:" + entity.value());
            addAssertions(assertionKeys, assertionReasons, graph.findByObject(entity), "entity_seed:" + entity.value());
        }
        for (String predicate : seedPredicates) {
            addAssertions(assertionKeys, assertionReasons, graph.findByPredicate(predicate), "predicate_seed:" + predicate);
        }

        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (assertionKeys.size() >= MAX_ASSERTIONS) {
                break;
            }
            if (seedPredicates.contains(assertion.predicate())
                    || seedEntities.contains(assertion.subject())
                    || seedEntities.contains(assertion.object())) {
                String key = FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
                if (assertionKeys.add(key)) {
                    addAssertionReason(assertionReasons, key, "seed_neighborhood");
                }
            }
        }

        for (RuleFrame rule : graph.getAllRuleFrames()) {
            if (ruleFrames.size() >= MAX_RULES) {
                break;
            }
            List<String> reasons = ruleMatchReasons(rule, seedEntities, seedPredicates);
            if (reasons.isEmpty()) {
                continue;
            }
            ruleFrames.add(rule);
            addRuleReasons(ruleReasons, rule, reasons);
        }

        Set<SymbolId> entityIds = new LinkedHashSet<>(seedEntities);
        expandEntitiesFromAssertions(graph, assertionKeys, entityIds, entityReasons);
        expandEntitiesFromRules(ruleFrames, entityIds, entityReasons);

        boolean reduced = assertionKeys.size() < graph.getAllAssertions().size()
                || ruleFrames.size() < graph.getAllRuleFrames().size()
                || entityIds.size() < graph.getAllEntities().size();
        FocusedKnowledgeBase view = new FocusedKnowledgeBase(graph, assertionKeys, ruleFrames, entityIds, reduced);
        return new SymbolicWorkingSet(
                view,
                reduced,
                buildEntities(entityIds, entityReasons),
                buildAssertions(graph, assertionKeys, assertionReasons),
                buildRules(ruleFrames, ruleReasons)
        );
    }

    private void collectQuerySeeds(QueryGoal query,
                                   Set<SymbolId> seedEntities,
                                   Set<String> seedPredicates,
                                   Map<SymbolId, LinkedHashSet<String>> entityReasons) {
        if (query == null) {
            return;
        }
        if (query.subject() != null && !query.subject().isBlank()) {
            SymbolId subject = new SymbolId(query.subject());
            seedEntities.add(subject);
            addEntityReason(entityReasons, subject, "query.subject");
        }
        if (query.object() != null && !query.object().isBlank()) {
            SymbolId object = new SymbolId(query.object());
            seedEntities.add(object);
            addEntityReason(entityReasons, object, "query.object");
        }
        if (query.predicate() != null && !query.predicate().isBlank()) {
            seedPredicates.add(query.predicate());
        }
    }

    private void collectWorkingMemorySeeds(WorkingMemory memory,
                                           Set<SymbolId> seedEntities,
                                           Set<String> seedPredicates,
                                           Set<String> assertionKeys,
                                           Map<SymbolId, LinkedHashSet<String>> entityReasons,
                                           Map<String, LinkedHashSet<String>> assertionReasons) {
        if (memory == null) {
            return;
        }
        List<SymbolId> active = memory.activeEntityOrder();
        for (int i = 0; i < active.size() && i < MAX_ACTIVE_ENTITIES; i++) {
            SymbolId entity = active.get(i);
            seedEntities.add(entity);
            addEntityReason(entityReasons, entity, "working_memory.active_entity");
        }

        List<QueryGoal> goals = memory.goalStack();
        for (int i = 0; i < goals.size() && i < MAX_GOALS; i++) {
            QueryGoal goal = goals.get(i);
            collectQuerySeeds(goal, seedEntities, seedPredicates, entityReasons);
            if (goal != null && goal.predicate() != null && !goal.predicate().isBlank()) {
                seedPredicates.add(goal.predicate());
            }
        }

        List<RelationAssertion> assertions = memory.recentAssertions();
        for (int i = 0; i < assertions.size() && i < MAX_RECENT_ASSERTIONS; i++) {
            RelationAssertion assertion = assertions.get(i);
            seedEntities.add(assertion.subject());
            seedEntities.add(assertion.object());
            seedPredicates.add(assertion.predicate());
            addEntityReason(entityReasons, assertion.subject(), "working_memory.recent_assertion_endpoint");
            addEntityReason(entityReasons, assertion.object(), "working_memory.recent_assertion_endpoint");
            String key = FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
            assertionKeys.add(key);
            addAssertionReason(assertionReasons, key, "working_memory.recent_assertion");
        }
    }

    private void addAssertions(Set<String> assertionKeys,
                               Map<String, LinkedHashSet<String>> assertionReasons,
                               List<RelationAssertion> assertions,
                               String reason) {
        if (assertions == null || assertions.isEmpty()) {
            return;
        }
        for (RelationAssertion assertion : assertions) {
            if (assertionKeys.size() >= MAX_ASSERTIONS) {
                return;
            }
            String key = FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
            assertionKeys.add(key);
            addAssertionReason(assertionReasons, key, reason);
        }
    }

    private List<String> ruleMatchReasons(RuleFrame rule, Set<SymbolId> seedEntities, Set<String> seedPredicates) {
        List<String> reasons = new ArrayList<>();
        if (rule == null) {
            return reasons;
        }
        for (RuleAtom atom : rule.antecedents()) {
            if (seedPredicates.contains(atom.predicate())) {
                reasons.add("predicate_seed:" + atom.predicate());
            }
            if (matchesRuleTerm(atom.subject(), seedEntities)) {
                reasons.add("entity_seed:" + atom.subject().value());
            }
            if (matchesRuleTerm(atom.object(), seedEntities)) {
                reasons.add("entity_seed:" + atom.object().value());
            }
        }
        RuleAtom consequent = rule.consequent();
        if (seedPredicates.contains(consequent.predicate())) {
            reasons.add("predicate_seed:" + consequent.predicate());
        }
        if (matchesRuleTerm(consequent.subject(), seedEntities)) {
            reasons.add("entity_seed:" + consequent.subject().value());
        }
        if (matchesRuleTerm(consequent.object(), seedEntities)) {
            reasons.add("entity_seed:" + consequent.object().value());
        }
        return reasons;
    }

    private boolean matchesRuleTerm(RuleTerm term, Set<SymbolId> seedEntities) {
        return term != null && !term.isVariable() && seedEntities.contains(new SymbolId(term.value()));
    }

    private void expandEntitiesFromAssertions(KnowledgeBase graph,
                                              Set<String> assertionKeys,
                                              Set<SymbolId> entityIds,
                                              Map<SymbolId, LinkedHashSet<String>> entityReasons) {
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!assertionKeys.contains(FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object()))) {
                continue;
            }
            entityIds.add(assertion.subject());
            entityIds.add(assertion.object());
            addEntityReason(entityReasons, assertion.subject(), "assertion_endpoint");
            addEntityReason(entityReasons, assertion.object(), "assertion_endpoint");
        }
    }

    private void expandEntitiesFromRules(Set<RuleFrame> ruleFrames,
                                         Set<SymbolId> entityIds,
                                         Map<SymbolId, LinkedHashSet<String>> entityReasons) {
        for (RuleFrame rule : ruleFrames) {
            for (RuleAtom atom : rule.antecedents()) {
                addRuleEntity(atom.subject(), entityIds, entityReasons);
                addRuleEntity(atom.object(), entityIds, entityReasons);
            }
            addRuleEntity(rule.consequent().subject(), entityIds, entityReasons);
            addRuleEntity(rule.consequent().object(), entityIds, entityReasons);
        }
    }

    private void addRuleEntity(RuleTerm term,
                               Set<SymbolId> entityIds,
                               Map<SymbolId, LinkedHashSet<String>> entityReasons) {
        if (term == null || term.isVariable()) {
            return;
        }
        SymbolId entity = new SymbolId(term.value());
        entityIds.add(entity);
        addEntityReason(entityReasons, entity, "rule_constant");
    }

    private void addEntityReason(Map<SymbolId, LinkedHashSet<String>> entityReasons, SymbolId entity, String reason) {
        entityReasons.computeIfAbsent(entity, ignored -> new LinkedHashSet<>()).add(reason);
    }

    private void addAssertionReason(Map<String, LinkedHashSet<String>> assertionReasons, String key, String reason) {
        assertionReasons.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(reason);
    }

    private void addRuleReasons(Map<RuleFrame, LinkedHashSet<String>> ruleReasons, RuleFrame rule, List<String> reasons) {
        LinkedHashSet<String> set = ruleReasons.computeIfAbsent(rule, ignored -> new LinkedHashSet<>());
        set.addAll(reasons);
    }

    private List<SymbolicWorkingSet.IncludedEntity> buildEntities(Set<SymbolId> entityIds,
                                                                  Map<SymbolId, LinkedHashSet<String>> entityReasons) {
        List<SymbolicWorkingSet.IncludedEntity> entities = new ArrayList<>();
        for (SymbolId entityId : entityIds) {
            entities.add(new SymbolicWorkingSet.IncludedEntity(
                    entityId,
                    List.copyOf(entityReasons.getOrDefault(entityId, new LinkedHashSet<>()))
            ));
        }
        return entities;
    }

    private List<SymbolicWorkingSet.IncludedAssertion> buildAssertions(KnowledgeBase graph,
                                                                       Set<String> assertionKeys,
                                                                       Map<String, LinkedHashSet<String>> assertionReasons) {
        List<SymbolicWorkingSet.IncludedAssertion> assertions = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String key = FocusedKnowledgeBase.assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
            if (!assertionKeys.contains(key)) {
                continue;
            }
            assertions.add(new SymbolicWorkingSet.IncludedAssertion(
                    assertion,
                    List.copyOf(assertionReasons.getOrDefault(key, new LinkedHashSet<>()))
            ));
        }
        return assertions;
    }

    private List<SymbolicWorkingSet.IncludedRule> buildRules(Set<RuleFrame> ruleFrames,
                                                             Map<RuleFrame, LinkedHashSet<String>> ruleReasons) {
        List<SymbolicWorkingSet.IncludedRule> rules = new ArrayList<>();
        for (RuleFrame rule : ruleFrames) {
            rules.add(new SymbolicWorkingSet.IncludedRule(
                    rule,
                    List.copyOf(ruleReasons.getOrDefault(rule, new LinkedHashSet<>()))
            ));
        }
        return rules;
    }
}
