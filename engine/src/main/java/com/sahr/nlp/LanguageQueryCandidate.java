package com.sahr.nlp;

import com.sahr.core.QueryGoal;

import java.util.Objects;

public final class LanguageQueryCandidate {
    private final QueryGoal queryGoal;
    private final double score;
    private final String producedBy;

    public LanguageQueryCandidate(QueryGoal queryGoal, double score, String producedBy) {
        this.queryGoal = Objects.requireNonNull(queryGoal, "queryGoal");
        this.score = score;
        this.producedBy = Objects.requireNonNull(producedBy, "producedBy");
    }

    public QueryGoal queryGoal() {
        return queryGoal;
    }

    public double score() {
        return score;
    }

    public String producedBy() {
        return producedBy;
    }
}
