package com.sahr.heads;

import java.util.List;
import java.util.Objects;

public final class OntologyHeadDefinition {
    public static final String EXECUTOR_PATTERN_MATCH = "PATTERN_MATCH";
    public static final String EXECUTOR_ASSERTION_INSERTION = "ASSERTION_INSERTION";
    public static final String EXECUTOR_RELATION_QUERY = "RELATION_QUERY";
    public static final String EXECUTOR_GRAPH_RETRIEVAL = "GRAPH_RETRIEVAL";
    public static final String EXECUTOR_QUERY_ALIGNMENT = "QUERY_ALIGNMENT";
    public static final String EXECUTOR_SUBGOAL_EXPANSION = "SUBGOAL_EXPANSION";

    private final String name;
    private final List<TriplePattern> patterns;
    private final TriplePattern action;
    private final double baseWeight;
    private final String executorType;
    private final java.util.Map<String, String> executorParams;

    public OntologyHeadDefinition(String name,
                                  List<TriplePattern> patterns,
                                  TriplePattern action,
                                  double baseWeight) {
        this(name, patterns, action, baseWeight, EXECUTOR_PATTERN_MATCH, java.util.Map.of());
    }

    public OntologyHeadDefinition(String name,
                                  List<TriplePattern> patterns,
                                  TriplePattern action,
                                  double baseWeight,
                                  String executorType,
                                  java.util.Map<String, String> executorParams) {
        this.name = name;
        this.patterns = List.copyOf(patterns);
        this.action = action;
        this.baseWeight = baseWeight;
        this.executorType = executorType == null || executorType.isBlank()
                ? EXECUTOR_PATTERN_MATCH
                : executorType.toUpperCase(java.util.Locale.ROOT);
        this.executorParams = executorParams == null ? java.util.Map.of() : java.util.Map.copyOf(executorParams);
    }

    public String name() {
        return name;
    }

    public List<TriplePattern> patterns() {
        return patterns;
    }

    public TriplePattern action() {
        return action;
    }

    public double baseWeight() {
        return baseWeight;
    }

    public String executorType() {
        return executorType;
    }

    public java.util.Map<String, String> executorParams() {
        return executorParams;
    }

    public String executorParam(String key) {
        if (key == null) {
            return null;
        }
        return executorParams.get(key);
    }

    public boolean isPatternMatch() {
        return EXECUTOR_PATTERN_MATCH.equals(executorType);
    }

    public static Term variable(String name) {
        return new Term(true, name);
    }

    public static Term constant(String value) {
        return new Term(false, value);
    }

    public static final class TriplePattern {
        private final Term subject;
        private final Term predicate;
        private final Term object;

        public TriplePattern(Term subject, Term predicate, Term object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        public Term subject() {
            return subject;
        }

        public Term predicate() {
            return predicate;
        }

        public Term object() {
            return object;
        }
    }

    public static final class Term {
        private final boolean variable;
        private final String value;

        public Term(boolean variable, String value) {
            this.variable = variable;
            this.value = value;
        }

        public boolean isVariable() {
            return variable;
        }

        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Term)) {
                return false;
            }
            Term that = (Term) other;
            return variable == that.variable && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(variable, value);
        }
    }
}
