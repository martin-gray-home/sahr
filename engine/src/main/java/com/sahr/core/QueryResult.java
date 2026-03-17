package com.sahr.core;

import java.util.Collections;
import java.util.List;

public final class QueryResult {
    private final QueryOperator operator;
    private final List<QueryBinding> bindings;
    private final long count;
    private final boolean exists;
    private final List<String> evidence;

    public QueryResult(QueryOperator operator,
                       List<QueryBinding> bindings,
                       long count,
                       boolean exists,
                       List<String> evidence) {
        this.operator = operator == null ? QueryOperator.RETRIEVE : operator;
        this.bindings = bindings == null ? List.of() : List.copyOf(bindings);
        this.count = count;
        this.exists = exists;
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public QueryOperator operator() {
        return operator;
    }

    public List<QueryBinding> bindings() {
        return Collections.unmodifiableList(bindings);
    }

    public long count() {
        return count;
    }

    public boolean exists() {
        return exists;
    }

    public List<String> evidence() {
        return Collections.unmodifiableList(evidence);
    }
}
