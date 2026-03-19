package com.sahr.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReasoningTraceEntry {
    private final QueryGoal query;
    private final InputSegmentOrigin segmentOrigin;
    private final SymbolicWorkingSet workingSet;
    private final List<DerivationTrace> derivations;
    private final List<ReasoningCandidate> candidates;
    private final ReasoningCandidate winner;

    public ReasoningTraceEntry(QueryGoal query,
                               InputSegmentOrigin segmentOrigin,
                               SymbolicWorkingSet workingSet,
                               List<DerivationTrace> derivations,
                               List<ReasoningCandidate> candidates,
                               ReasoningCandidate winner) {
        this.query = Objects.requireNonNull(query, "query");
        this.segmentOrigin = segmentOrigin;
        this.workingSet = workingSet;
        this.derivations = Collections.unmodifiableList(derivations == null ? List.of() : derivations);
        this.candidates = Collections.unmodifiableList(Objects.requireNonNull(candidates, "candidates"));
        this.winner = Objects.requireNonNull(winner, "winner");
    }

    public QueryGoal query() {
        return query;
    }

    public InputSegmentOrigin segmentOrigin() {
        return segmentOrigin;
    }

    public SymbolicWorkingSet workingSet() {
        return workingSet;
    }

    public List<DerivationTrace> derivations() {
        return derivations;
    }

    public List<ReasoningCandidate> candidates() {
        return candidates;
    }

    public ReasoningCandidate winner() {
        return winner;
    }
}
