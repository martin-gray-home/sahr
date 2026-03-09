package com.sahr.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorkingMemory {
    private static final int MAX_ACTIVE_ENTITIES = 100;
    private static final int MAX_RECENT_ASSERTIONS = 50;

    private final Set<SymbolId> activeEntities = new HashSet<>();
    private final Deque<SymbolId> activeEntityOrder = new ArrayDeque<>();
    private final Deque<RelationAssertion> recentAssertions = new ArrayDeque<>();
    private final Deque<QueryGoal> goalStack = new ArrayDeque<>();

    public void addActiveEntity(SymbolId entity) {
        if (entity == null) {
            return;
        }
        if (activeEntities.add(entity)) {
            activeEntityOrder.addFirst(entity);
        } else {
            activeEntityOrder.remove(entity);
            activeEntityOrder.addFirst(entity);
        }
        trimActiveEntities();
    }

    public void addActiveEntities(Set<SymbolId> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (SymbolId entity : entities) {
            addActiveEntity(entity);
        }
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
        activeEntityOrder.clear();
        recentAssertions.clear();
        goalStack.clear();
    }

    private void trimActiveEntities() {
        while (activeEntityOrder.size() > MAX_ACTIVE_ENTITIES) {
            SymbolId evicted = activeEntityOrder.removeLast();
            activeEntities.remove(evicted);
        }
    }
}
