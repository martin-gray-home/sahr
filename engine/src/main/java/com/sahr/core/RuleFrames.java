package com.sahr.core;

import java.util.List;
import java.util.Optional;

public final class RuleFrames {
    private static final String LEGACY_VARIABLE = "_";

    private RuleFrames() {
    }

    public static RuleFrame fromLegacyRuleAssertion(RuleAssertion rule) {
        if (rule == null) {
            return null;
        }
        return new RuleFrame(
                LEGACY_VARIABLE,
                List.of(toAtom(rule.antecedent())),
                toAtom(rule.consequent()),
                rule.confidence()
        );
    }

    public static Optional<RuleAssertion> toLegacyRuleAssertion(RuleFrame rule) {
        if (!isLegacyCompatible(rule)) {
            return Optional.empty();
        }
        RelationAssertion antecedent = legacyAntecedent(rule).orElse(null);
        RelationAssertion consequent = legacyConsequent(rule).orElse(null);
        if (antecedent == null || consequent == null) {
            return Optional.empty();
        }
        return Optional.of(new RuleAssertion(antecedent, consequent, rule.confidence()));
    }

    public static Optional<RelationAssertion> legacyAntecedent(RuleFrame rule) {
        if (!isLegacyCompatible(rule)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toAssertion(rule.antecedents().get(0), rule.confidence()));
    }

    public static Optional<RelationAssertion> legacyConsequent(RuleFrame rule) {
        if (!isLegacyCompatible(rule)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toAssertion(rule.consequent(), rule.confidence()));
    }

    public static boolean isLegacyCompatible(RuleFrame rule) {
        if (rule == null || rule.antecedents().size() != 1) {
            return false;
        }
        return isGroundAtom(rule.antecedents().get(0)) && isGroundAtom(rule.consequent());
    }

    public static boolean usesVariable(RuleFrame rule) {
        if (rule == null) {
            return false;
        }
        for (RuleAtom antecedent : rule.antecedents()) {
            if (usesVariable(antecedent)) {
                return true;
            }
        }
        return usesVariable(rule.consequent());
    }

    private static boolean usesVariable(RuleAtom atom) {
        return atom != null && (atom.subject().isVariable() || atom.object().isVariable());
    }

    private static boolean isGroundAtom(RuleAtom atom) {
        return atom != null && !atom.subject().isVariable() && !atom.object().isVariable();
    }

    private static RuleAtom toAtom(RelationAssertion assertion) {
        return new RuleAtom(
                RuleTerm.constant(assertion.subject().value()),
                assertion.predicate(),
                RuleTerm.constant(assertion.object().value())
        );
    }

    private static RelationAssertion toAssertion(RuleAtom atom, double confidence) {
        if (!isGroundAtom(atom)) {
            return null;
        }
        return new RelationAssertion(
                new SymbolId(atom.subject().value()),
                atom.predicate(),
                new SymbolId(atom.object().value()),
                confidence
        );
    }
}
