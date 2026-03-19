package com.sahr.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SymbolicWorkingSet {
    private final KnowledgeBase view;
    private final boolean reduced;
    private final List<IncludedEntity> entities;
    private final List<IncludedAssertion> assertions;
    private final List<IncludedRule> rules;

    public SymbolicWorkingSet(KnowledgeBase view,
                              boolean reduced,
                              List<IncludedEntity> entities,
                              List<IncludedAssertion> assertions,
                              List<IncludedRule> rules) {
        this.view = Objects.requireNonNull(view, "view");
        this.reduced = reduced;
        this.entities = entities == null ? List.of() : List.copyOf(entities);
        this.assertions = assertions == null ? List.of() : List.copyOf(assertions);
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public KnowledgeBase view() {
        return view;
    }

    public boolean reduced() {
        return reduced;
    }

    public List<IncludedEntity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public List<IncludedAssertion> assertions() {
        return Collections.unmodifiableList(assertions);
    }

    public List<IncludedRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    public record IncludedEntity(SymbolId entity, List<String> reasons) {
        public IncludedEntity {
            Objects.requireNonNull(entity, "entity");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record IncludedAssertion(RelationAssertion assertion, List<String> reasons) {
        public IncludedAssertion {
            Objects.requireNonNull(assertion, "assertion");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record IncludedRule(RuleFrame rule, List<String> reasons) {
        public IncludedRule {
            Objects.requireNonNull(rule, "rule");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}
