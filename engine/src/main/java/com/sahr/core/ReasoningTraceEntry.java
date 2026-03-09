package com.sahr.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReasoningTraceEntry {
    private final QueryGoal query;
    private final List<ReasoningCandidate> candidates;
    private final ReasoningCandidate winner;

    public ReasoningTraceEntry(QueryGoal query, List<ReasoningCandidate> candidates, ReasoningCandidate winner) {
        this.query = Objects.requireNonNull(query, "query");
        this.candidates = Collections.unmodifiableList(Objects.requireNonNull(candidates, "candidates"));
        this.winner = Objects.requireNonNull(winner, "winner");
    }

    public QueryGoal query() {
        return query;
    }

    public List<ReasoningCandidate> candidates() {
        return candidates;
    }

    public ReasoningCandidate winner() {
        return winner;
    }
}
