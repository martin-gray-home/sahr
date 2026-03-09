package com.sahr.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorkingMemory {
    private static final int MAX_RECENT_ASSERTIONS = 50;

    private final Set<SymbolId> activeEntities = new HashSet<>();
    private final Deque<RelationAssertion> recentAssertions = new ArrayDeque<>();
    private final Deque<QueryGoal> goalStack = new ArrayDeque<>();

    public void addActiveEntity(SymbolId entity) {
        if (entity == null) {
            return;
        }
        activeEntities.add(entity);
    }

    public void addActiveEntities(Set<SymbolId> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        activeEntities.addAll(entities);
    }

    public boolean isActiveEntity(SymbolId entity) {
        return entity != null && activeEntities.contains(entity);
    }

    public Set<SymbolId> activeEntities() {
        return Collections.unmodifiableSet(activeEntities);
    }

    public void recordAssertion(RelationAssertion assertion) {
        if (assertion == null) {
            return;
        }
        recentAssertions.addFirst(assertion);
        while (recentAssertions.size() > MAX_RECENT_ASSERTIONS) {
            recentAssertions.removeLast();
        }
    }

    public List<RelationAssertion> recentAssertions() {
        return List.copyOf(recentAssertions);
    }

    public void pushGoal(QueryGoal goal) {
        if (goal == null) {
            return;
        }
        goalStack.push(goal);
    }

    public void popGoal() {
        if (!goalStack.isEmpty()) {
            goalStack.pop();
        }
    }

    public List<QueryGoal> goalStack() {
        return List.copyOf(goalStack);
    }

    public void clear() {
        activeEntities.clear();
        recentAssertions.clear();
        goalStack.clear();
    }
}
