package com.sahr.nlp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StatementBatch {
    private final List<Statement> statements;

    private StatementBatch(List<Statement> statements) {
        this.statements = Collections.unmodifiableList(Objects.requireNonNull(statements, "statements"));
    }

    public static StatementBatch from(Statement primary) {
        List<Statement> batch = new ArrayList<>();
        batch.add(primary);
        batch.addAll(primary.additionalStatements());
        return new StatementBatch(batch);
    }

    public List<Statement> statements() {
        return statements;
    }
}
