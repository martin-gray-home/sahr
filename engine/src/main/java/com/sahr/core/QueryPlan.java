package com.sahr.core;

import java.util.List;
import java.util.Objects;

public final class QueryPlan {
    private final QueryGoal goal;
    private final List<String> evidence;

    public QueryPlan(QueryGoal goal, List<String> evidence) {
        this.goal = Objects.requireNonNull(goal, "goal");
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public QueryGoal goal() {
        return goal;
    }

    public List<String> evidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "QueryPlan{" + "goal=" + goal + ", evidence=" + evidence + '}';
    }
}
