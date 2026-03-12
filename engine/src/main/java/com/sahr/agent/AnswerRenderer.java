package com.sahr.agent;

import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;

import java.util.Locale;

final class AnswerRenderer {
    interface DisplayFormatter {
        String localName(String predicate);

        Boolean booleanConcept(SymbolId id);

        String normalizeTypeToken(String raw);
    }

    private final DisplayFormatter formatter;

    AnswerRenderer(DisplayFormatter formatter) {
        this.formatter = formatter;
    }

    String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect) {
        String predicate = formatter.localName(assertion.predicate());
        if ("causedby".equals(predicate)) {
            return displayValue(effect) + " is caused by " + displayValue(cause) + ".";
        }
        return displayValue(cause) + " causes " + displayValue(effect) + ".";
    }

    String formatRuleSentence(RuleAssertion rule) {
        RelationAssertion antecedent = rule.antecedent();
        RelationAssertion consequent = rule.consequent();
        return "If " + formatAssertionClause(antecedent) + ", then " + formatAssertionClause(consequent) + ".";
    }

    String formatAssertionSentence(RelationAssertion assertion) {
        return formatAssertionClause(assertion) + ".";
    }

    private String formatAssertionClause(RelationAssertion assertion) {
        if (assertion == null) {
            return "unknown";
        }
        SymbolId subject = assertion.subject();
        SymbolId object = assertion.object();
        String predicate = formatter.localName(assertion.predicate());
        Boolean booleanValue = formatter.booleanConcept(object);
        String subjectText = displayValue(subject);
        if (booleanValue != null) {
            if ("fail".equals(predicate)) {
                return subjectText + " " + selectVerbForm(subjectText, booleanValue ? "fail" : "does not fail");
            }
            if ("operate".equals(predicate) || "function".equals(predicate)
                    || "work".equals(predicate) || "respond".equals(predicate)
                    || "stop_responding".equals(predicate) || "stop".equals(predicate)) {
                return subjectText + " " + selectVerbForm(subjectText, booleanValue ? "operate" : "does not operate");
            }
            if ("become_unstable".equals(predicate) || "unstable".equals(predicate)) {
                return subjectText + " " + selectVerbForm(subjectText, booleanValue ? "becomes unstable" : "remains stable");
            }
            return subjectText + (booleanValue ? " " + displayPredicate(assertion.predicate())
                    : " does not " + displayPredicate(assertion.predicate()));
        }
        if ("backupfor".equals(predicate) || "backup_for".equals(predicate)) {
            return subjectText + " is a backup for " + displayValue(object);
        }
        if ("poweredby".equals(predicate) || "powered_by".equals(predicate)) {
            return subjectText + " is powered by " + displayValue(object);
        }
        if ("restore".equals(predicate)) {
            return subjectText + " restores " + displayValue(object);
        }
        return subjectText + " " + displayPredicate(assertion.predicate()) + " " + displayValue(object);
    }

    private String selectVerbForm(String subjectText, String base) {
        if (subjectText == null || subjectText.isBlank()) {
            return base;
        }
        String normalized = subjectText.trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("\\s+");
        String last = tokens[tokens.length - 1];
        boolean plural = last.endsWith("s") && !last.endsWith("ss");
        if (!plural) {
            if (base.startsWith("does not ")) {
                return base;
            }
            if (base.startsWith("do not ")) {
                return base.replaceFirst("do not ", "does not ");
            }
            if (!base.contains(" ") && !base.endsWith("s")) {
                return base + "s";
            }
            return base;
        }
        if (base.startsWith("does not ")) {
            return base.replaceFirst("does not ", "do not ");
        }
        if (base.endsWith("s") && base.split("\\s+").length == 1) {
            return base.substring(0, base.length() - 1);
        }
        if ("is".equals(base)) {
            return "are";
        }
        if ("was".equals(base)) {
            return "were";
        }
        return base;
    }

    private String displayValue(SymbolId id) {
        if (id == null || id.value() == null) {
            return "unknown";
        }
        String value = id.value();
        if (value.startsWith("entity:") || value.startsWith("concept:")) {
            value = value.substring(value.indexOf(':') + 1);
        }
        value = value.replace('_', ' ');
        return value;
    }

    private String displayPredicate(String predicate) {
        String local = formatter.localName(predicate);
        if (local.isBlank()) {
            return "related to";
        }
        return local.replace('_', ' ');
    }
}
