package com.sahr.core;

import java.util.Locale;
import java.util.Set;

public final class QueryNormalizer {
    private static final Set<String> WILDCARD_TOKENS = Set.of(
            "anything",
            "something",
            "anyone",
            "someone",
            "somebody",
            "anybody"
    );

    public QueryFrame normalize(QueryGoal query, QueryOperator operator, String expectedType) {
        QueryOperator resolvedOperator = operator == null ? operatorFor(query) : operator;
        String subject = query == null ? null : query.subject();
        String object = query == null ? null : query.object();
        String modifier = query == null ? null : query.modifier();
        String discourse = query == null ? null : query.discourseModifier();

        if (isDiscourseModifier(discourse)) {
            modifier = null;
        }

        if (query != null && query.type() == QueryGoal.Type.YESNO) {
            if (isWildcard(subject)) {
                subject = null;
            }
            if (isWildcard(object)) {
                object = null;
            }
        }

        QueryFrame.TargetSlot targetSlot = QueryFrame.TargetSlot.ANY;
        boolean hasSubject = subject != null && !subject.isBlank();
        boolean hasObject = object != null && !object.isBlank();
        if (hasSubject && !hasObject) {
            targetSlot = QueryFrame.TargetSlot.OBJECT;
        } else if (hasObject && !hasSubject) {
            targetSlot = QueryFrame.TargetSlot.SUBJECT;
        }

        return new QueryFrame(
                resolvedOperator,
                subject,
                query == null ? null : query.predicate(),
                object,
                targetSlot,
                expectedType,
                modifier,
                true
        );
    }

    private QueryOperator operatorFor(QueryGoal query) {
        if (query == null) {
            return QueryOperator.RETRIEVE;
        }
        return switch (query.type()) {
            case COUNT -> QueryOperator.COUNT;
            case YESNO -> QueryOperator.EXISTS;
            default -> QueryOperator.RETRIEVE;
        };
    }

    private boolean isWildcard(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("entity:")) {
            normalized = normalized.substring("entity:".length());
        } else if (normalized.startsWith("concept:")) {
            normalized = normalized.substring("concept:".length());
        }
        return WILDCARD_TOKENS.contains(normalized);
    }

    private boolean isDiscourseModifier(String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return false;
        }
        String normalized = modifier.toLowerCase(Locale.ROOT);
        return "else".equals(normalized) || "other".equals(normalized) || "another".equals(normalized);
    }
}
