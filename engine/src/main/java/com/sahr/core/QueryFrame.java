package com.sahr.core;

import java.util.Objects;

public final class QueryFrame {
    public enum TargetSlot {
        SUBJECT,
        OBJECT,
        ANY
    }

    private final QueryOperator operator;
    private final String subject;
    private final String predicate;
    private final String object;
    private final TargetSlot targetSlot;
    private final String typeConstraint;
    private final boolean includeInferred;

    public QueryFrame(QueryOperator operator,
                      String subject,
                      String predicate,
                      String object,
                      TargetSlot targetSlot,
                      String typeConstraint,
                      boolean includeInferred) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.targetSlot = targetSlot == null ? TargetSlot.ANY : targetSlot;
        this.typeConstraint = typeConstraint;
        this.includeInferred = includeInferred;
    }

    public QueryOperator operator() {
        return operator;
    }

    public String subject() {
        return subject;
    }

    public String predicate() {
        return predicate;
    }

    public String object() {
        return object;
    }

    public TargetSlot targetSlot() {
        return targetSlot;
    }

    public String typeConstraint() {
        return typeConstraint;
    }

    public boolean includeInferred() {
        return includeInferred;
    }
}
