package com.sahr.core;

import java.util.Objects;

public final class QueryGoal {
    public enum Type {
        WHERE,
        RELATION,
        YESNO,
        UNKNOWN
    }

    private final Type type;
    private final String subject;
    private final String object;
    private final String predicate;
    private final String expectedType;
    private final String entityType;
    private final String expectedRange;
    private final String subjectText;
    private final String objectText;
    private final String predicateText;

    public QueryGoal(Type type,
                     String subject,
                     String object,
                     String predicate,
                     String expectedType,
                     String entityType,
                     String expectedRange,
                     String subjectText,
                     String objectText,
                     String predicateText) {
        this.type = Objects.requireNonNull(type, "type");
        this.subject = subject;
        this.object = object;
        this.predicate = predicate;
        this.expectedType = expectedType;
        this.entityType = entityType;
        this.expectedRange = expectedRange;
        this.subjectText = subjectText;
        this.objectText = objectText;
        this.predicateText = predicateText;
    }

    public static QueryGoal unknown() {
        return new QueryGoal(Type.UNKNOWN, null, null, null, null, null, null, null, null, null);
    }

    public static QueryGoal where(String entityType, String expectedRange) {
        return new QueryGoal(Type.WHERE, null, null, null, null, entityType, expectedRange, null, null, null);
    }

    public static QueryGoal relation(String subject, String predicate, String object, String expectedType) {
        return new QueryGoal(Type.RELATION, subject, object, predicate, expectedType, null, null, null, null, null);
    }

    public static QueryGoal yesNo(String subject,
                                  String predicate,
                                  String object,
                                  String expectedType,
                                  String subjectText,
                                  String objectText,
                                  String predicateText) {
        return new QueryGoal(Type.YESNO, subject, object, predicate, expectedType, null, null, subjectText, objectText, predicateText);
    }

    public Type type() {
        return type;
    }

    public String subject() {
        return subject;
    }

    public String object() {
        return object;
    }

    public String predicate() {
        return predicate;
    }

    public String expectedType() {
        return expectedType;
    }

    public String entityType() {
        return entityType;
    }

    public String expectedRange() {
        return expectedRange;
    }

    public String subjectText() {
        return subjectText;
    }

    public String objectText() {
        return objectText;
    }

    public String predicateText() {
        return predicateText;
    }

    public boolean isQuestion() {
        return type != Type.UNKNOWN;
    }
}
