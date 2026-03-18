package com.sahr.core;

import java.util.Objects;

public final class RuleTerm {
    private final boolean variable;
    private final String value;

    private RuleTerm(boolean variable, String value) {
        this.variable = variable;
        this.value = Objects.requireNonNull(value, "value");
    }

    public static RuleTerm variable(String name) {
        return new RuleTerm(true, name);
    }

    public static RuleTerm constant(String value) {
        return new RuleTerm(false, value);
    }

    public boolean isVariable() {
        return variable;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return variable ? "?" + value : value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuleTerm)) {
            return false;
        }
        RuleTerm that = (RuleTerm) other;
        return variable == that.variable && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, value);
    }
}
