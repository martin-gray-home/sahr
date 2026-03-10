package com.sahr.core;

import java.util.Objects;

public final class QueryGoal {
    public enum Type {
        WHERE,
        RELATION,
        YESNO,
        ATTRIBUTE,
        COUNT,
        UNKNOWN
    }

    private final Type type;
    private final String subject;
    private final String object;
    private final String predicate;
    private final String expectedType;
    private final String entityType;
    private final String expectedRange;
    private final String attribute;
    private final String modifier;
    private final String subjectText;
    private final String objectText;
    private final String predicateText;
    private final String goalId;
    private final String parentGoalId;
    private final int depth;

    public QueryGoal(Type type,
                     String subject,
                     String object,
                     String predicate,
                     String expectedType,
                     String entityType,
                     String expectedRange,
                     String attribute,
                     String modifier,
                     String subjectText,
                     String objectText,
                     String predicateText) {
        this(type, subject, object, predicate, expectedType, entityType, expectedRange, attribute, modifier, subjectText, objectText, predicateText,
                java.util.UUID.randomUUID().toString(), null, 0);
    }

    public QueryGoal(Type type,
                     String subject,
                     String object,
                     String predicate,
                     String expectedType,
                     String entityType,
                     String expectedRange,
                     String attribute,
                     String modifier,
                     String subjectText,
                     String objectText,
                     String predicateText,
                     String goalId,
                     String parentGoalId,
                     int depth) {
        this.type = Objects.requireNonNull(type, "type");
        this.subject = subject;
        this.object = object;
        this.predicate = predicate;
        this.expectedType = expectedType;
        this.entityType = entityType;
        this.expectedRange = expectedRange;
        this.attribute = attribute;
        this.modifier = modifier;
        this.subjectText = subjectText;
        this.objectText = objectText;
        this.predicateText = predicateText;
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.parentGoalId = parentGoalId;
        this.depth = depth;
    }

    public static QueryGoal unknown() {
        return new QueryGoal(Type.UNKNOWN, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static QueryGoal where(String entityType, String expectedRange) {
        return new QueryGoal(Type.WHERE, null, null, null, null, entityType, expectedRange, null, null, null, null, null);
    }

    public static QueryGoal relation(String subject, String predicate, String object, String expectedType) {
        return new QueryGoal(Type.RELATION, subject, object, predicate, expectedType, null, null, null, null, null, null, null);
    }

    public static QueryGoal relationWithModifier(String subject,
                                                 String predicate,
                                                 String object,
                                                 String expectedType,
                                                 String modifier) {
        return new QueryGoal(Type.RELATION, subject, object, predicate, expectedType, null, null, null, modifier, null, null, null);
    }

    public static QueryGoal yesNo(String subject,
                                  String predicate,
                                  String object,
                                  String expectedType,
                                  String subjectText,
                                  String objectText,
                                  String predicateText) {
        return new QueryGoal(Type.YESNO, subject, object, predicate, expectedType, null, null, null, null, subjectText, objectText, predicateText);
    }

    public static QueryGoal attribute(String subject, String attribute) {
        return new QueryGoal(Type.ATTRIBUTE, subject, null, "hasAttribute", null, null, null, attribute, null, null, null, null);
    }

    public static QueryGoal count(String subject,
                                  String predicate,
                                  String object,
                                  String expectedType,
                                  String modifier) {
        return new QueryGoal(Type.COUNT, subject, object, predicate, expectedType, null, null, null, modifier, null, null, null);
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

    public String attribute() {
        return attribute;
    }

    public String modifier() {
        return modifier;
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

    public String goalId() {
        return goalId;
    }

    public String parentGoalId() {
        return parentGoalId;
    }

    public int depth() {
        return depth;
    }

    public QueryGoal withParent(String parentGoalId, int depth) {
        return new QueryGoal(
                type,
                subject,
                object,
                predicate,
                expectedType,
                entityType,
                expectedRange,
                attribute,
                modifier,
                subjectText,
                objectText,
                predicateText,
                java.util.UUID.randomUUID().toString(),
                parentGoalId,
                depth
        );
    }

    public boolean isQuestion() {
        return type != Type.UNKNOWN;
    }
}
