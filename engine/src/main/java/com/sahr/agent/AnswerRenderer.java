package com.sahr.agent;

import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.SahrAnnotationVocabulary;

import java.util.Locale;

final class AnswerRenderer {
    interface DisplayFormatter {
        String localName(String predicate);

        Boolean booleanConcept(SymbolId id);

        String normalizeTypeToken(String raw);
    }

    private final DisplayFormatter formatter;
    private final OntologyAnnotationResolver resolver;

    AnswerRenderer(DisplayFormatter formatter, OntologyAnnotationResolver resolver) {
        this.formatter = formatter;
        this.resolver = resolver;
    }

    String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect) {
        String predicate = formatter.localName(assertion.predicate());
        String template = resolveTemplate(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE);
        if (template != null) {
            return applyTemplate(template, null, null, assertion.predicate(), cause, effect);
        }
        if ("causedby".equals(predicate)) {
            return displayValue(effect) + " is caused by " + displayValue(cause) + ".";
        }
        return displayValue(cause) + " causes " + displayValue(effect) + ".";
    }

    String formatRuleSentence(RuleAssertion rule) {
        RelationAssertion antecedent = rule.antecedent();
        RelationAssertion consequent = rule.consequent();
        String consequentPredicate = formatter.localName(consequent.predicate());
        if ("backupfor".equals(consequentPredicate) || "backup_for".equals(consequentPredicate)) {
            String subject = displayValue(consequent.subject());
            String object = displayValue(consequent.object());
            String antecedentText = formatAssertionClause(antecedent);
            return subject + " can serve as a backup for " + object + " when " + antecedentText + ".";
        }
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
            String template = booleanValue
                    ? resolveTemplate(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE_TRUE)
                    : resolveTemplate(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE_FALSE);
            if (template != null) {
                return applyTemplate(template, subject, object, assertion.predicate(), null, null);
            }
        } else {
            String template = resolveTemplate(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE);
            if (template != null) {
                return applyTemplate(template, subject, object, assertion.predicate(), null, null);
            }
        }
        if (booleanValue != null) {
            if ("fail".equals(predicate)) {
                return subjectText + " " + selectVerbForm(subjectText, booleanValue ? "fail" : "does not fail");
            }
            if ("operate".equals(predicate) || "function".equals(predicate)
                    || "work".equals(predicate) || "respond".equals(predicate)
                    || "stop_responding".equals(predicate) || "stop".equals(predicate)) {
                return subjectText + " " + selectVerbForm(subjectText, booleanValue ? "operate" : "does not operate");
            }
            if ("control".equals(predicate) && !booleanValue) {
                String normalizedTarget = normalizeControlTarget(assertion.object());
                if (normalizedTarget != null) {
                    return "Loss of " + normalizedTarget;
                }
                return "Loss of " + subjectText;
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
        if ("call".equals(local)) {
            return "contains";
        }
        return local.replace('_', ' ');
    }

    private String normalizeControlTarget(SymbolId object) {
        if (object == null || object.value() == null) {
            return null;
        }
        String value = object.value();
        if (value.startsWith("concept:")) {
            value = value.substring("concept:".length());
        } else if (value.startsWith("entity:")) {
            value = value.substring("entity:".length());
        }
        if (value.contains("control_spacecraft_orientation") || value.contains("spacecraft_orientation_control")) {
            return "spacecraft orientation control";
        }
        return value.replace('_', ' ') + " control";
    }

    private String resolveTemplate(String predicate, String annotationIri) {
        if (resolver == null) {
            return null;
        }
        return resolver.resolveObjectPropertyIri(predicate)
                .flatMap(iri -> resolver.annotationValue(iri, annotationIri))
                .orElse(null);
    }

    private String applyTemplate(String template,
                                 SymbolId subject,
                                 SymbolId object,
                                 String predicate,
                                 SymbolId cause,
                                 SymbolId effect) {
        String rendered = template;
        if (subject != null) {
            rendered = rendered.replace("{subject}", displayValue(subject));
        }
        if (object != null) {
            rendered = rendered.replace("{object}", displayValue(object));
        }
        if (predicate != null) {
            rendered = rendered.replace("{predicate}", displayPredicate(predicate));
        }
        if (cause != null) {
            rendered = rendered.replace("{cause}", displayValue(cause));
        }
        if (effect != null) {
            rendered = rendered.replace("{effect}", displayValue(effect));
        }
        return rendered;
    }
}
