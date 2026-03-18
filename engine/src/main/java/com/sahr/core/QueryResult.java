package com.sahr.core;

import java.util.Collections;
import java.util.List;

public final class QueryResult {
    private final QueryOperator operator;
    private final List<QueryBinding> bindings;
    private final List<RelationAssertion> facts;
    private final long count;
    private final boolean exists;
    private final List<String> evidence;

    public QueryResult(QueryOperator operator,
                       List<QueryBinding> bindings,
                       long count,
                       boolean exists,
                       List<String> evidence) {
        this(operator, bindings, count, exists, evidence, List.of());
    }

    public QueryResult(QueryOperator operator,
                       List<QueryBinding> bindings,
                       long count,
                       boolean exists,
                       List<String> evidence,
                       List<RelationAssertion> facts) {
        this.operator = operator == null ? QueryOperator.RETRIEVE : operator;
        this.bindings = bindings == null ? List.of() : List.copyOf(bindings);
        this.facts = facts == null ? List.of() : List.copyOf(facts);
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

    public List<RelationAssertion> facts() {
        return Collections.unmodifiableList(facts);
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
