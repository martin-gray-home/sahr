package com.sahr.heads;

import java.util.List;
import java.util.Objects;

public final class OntologyHeadDefinition {
    private final String name;
    private final List<TriplePattern> patterns;
    private final TriplePattern action;
    private final double baseWeight;

    public OntologyHeadDefinition(String name,
                                  List<TriplePattern> patterns,
                                  TriplePattern action,
                                  double baseWeight) {
        this.name = name;
        this.patterns = List.copyOf(patterns);
        this.action = action;
        this.baseWeight = baseWeight;
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
