package com.sahr.core;

import java.util.Objects;

public final class RuleAtom {
    private final RuleTerm subject;
    private final String predicate;
    private final RuleTerm object;

    public RuleAtom(RuleTerm subject, String predicate, RuleTerm object) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.object = Objects.requireNonNull(object, "object");
    }

    public RuleTerm subject() {
        return subject;
    }

    public String predicate() {
        return predicate;
    }

    public RuleTerm object() {
        return object;
    }

    @Override
    public String toString() {
        return subject + " " + predicate + " " + object;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuleAtom)) {
            return false;
        }
        RuleAtom that = (RuleAtom) other;
        return subject.equals(that.subject)
                && predicate.equals(that.predicate)
                && object.equals(that.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, predicate, object);
    }
}
