package com.sahr.core;

import java.util.List;
import java.util.Objects;

public final class QueryPlan {
    public enum Kind {
        RELATION_MATCH,
        TEMPORAL_MATCH,
        CAUSE_CHAIN,
        EVIDENCE_MATCH
    }

    private final Kind kind;
    private final QueryGoal goal;
    private final List<String> evidence;

    public QueryPlan(Kind kind, QueryGoal goal, List<String> evidence) {
        this.kind = kind == null ? Kind.RELATION_MATCH : kind;
        this.goal = Objects.requireNonNull(goal, "goal");
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public Kind kind() {
        return kind;
    }

    public QueryGoal goal() {
        return goal;
    }

    public List<String> evidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return "QueryPlan{" + "kind=" + kind + ", goal=" + goal + ", evidence=" + evidence + '}';
    }
}
