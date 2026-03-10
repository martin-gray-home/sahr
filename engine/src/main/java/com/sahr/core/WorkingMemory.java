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

    private final ReasoningPhaseCoordinator phases;
    private final Set<SymbolId> activeEntities = new HashSet<>();
    private final Deque<SymbolId> activeEntityOrder = new ArrayDeque<>();
    private final Deque<RelationAssertion> recentAssertions = new ArrayDeque<>();
    private final Deque<QueryGoal> goalStack = new ArrayDeque<>();

    public WorkingMemory() {
        this(null);
    }

    public WorkingMemory(ReasoningPhaseCoordinator phases) {
        this.phases = phases;
    }

    public void addActiveEntity(SymbolId entity) {
        assertUpdate("addActiveEntity");
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
        assertUpdate("addActiveEntities");
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
        assertUpdate("recordAssertion");
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
        assertUpdate("pushGoal");
        if (goal == null) {
            return;
        }
        goalStack.push(goal);
    }

    public void popGoal() {
        assertUpdate("popGoal");
        if (!goalStack.isEmpty()) {
            goalStack.pop();
        }
    }

    public List<QueryGoal> goalStack() {
        return List.copyOf(goalStack);
    }

    public void clear() {
        assertUpdate("clear");
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

    private void assertUpdate(String operation) {
        if (phases != null) {
            phases.assertUpdatePhase(operation);
        }
    }
}
