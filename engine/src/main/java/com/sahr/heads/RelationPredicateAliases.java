package com.sahr.heads;

import java.util.HashSet;
import java.util.Set;

final class RelationPredicateAliases {
    private static final String SAHR_RELATIONS_NS = "https://sahr.ai/ontology/relations#";

    private RelationPredicateAliases() {
    }

    static Set<String> withSahrIriAliases(Set<String> predicates) {
        Set<String> expanded = new HashSet<>(predicates);
        for (String predicate : predicates) {
            if (!isIri(predicate)) {
                expanded.add(SAHR_RELATIONS_NS + predicate);
            }
        }
        return expanded;
    }

    static boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }
}
