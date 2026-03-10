package com.sahr.core;

import java.util.Objects;

public final class QueryKey {
    private final QueryGoal.Type type;
    private final String subject;
    private final String object;
    private final String predicate;
    private final String expectedType;
    private final String entityType;
    private final String expectedRange;
    private final String attribute;
    private final String modifier;

    private QueryKey(QueryGoal.Type type,
                     String subject,
                     String object,
                     String predicate,
                     String expectedType,
                     String entityType,
                     String expectedRange,
                     String attribute,
                     String modifier) {
        this.type = Objects.requireNonNull(type, "type");
        this.subject = subject;
        this.object = object;
        this.predicate = predicate;
        this.expectedType = expectedType;
        this.entityType = entityType;
        this.expectedRange = expectedRange;
        this.attribute = attribute;
        this.modifier = modifier;
    }

    public static QueryKey from(QueryGoal query) {
        if (query == null) {
            return null;
        }
        return new QueryKey(
                query.type(),
                query.subject(),
                query.object(),
                query.predicate(),
                query.expectedType(),
                query.entityType(),
                query.expectedRange(),
                query.attribute(),
                query.modifier()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QueryKey)) {
            return false;
        }
        QueryKey that = (QueryKey) other;
        return type == that.type
                && Objects.equals(subject, that.subject)
                && Objects.equals(object, that.object)
                && Objects.equals(predicate, that.predicate)
                && Objects.equals(expectedType, that.expectedType)
                && Objects.equals(entityType, that.entityType)
                && Objects.equals(expectedRange, that.expectedRange)
                && Objects.equals(attribute, that.attribute)
                && Objects.equals(modifier, that.modifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subject, object, predicate, expectedType, entityType, expectedRange, attribute, modifier);
    }
}
