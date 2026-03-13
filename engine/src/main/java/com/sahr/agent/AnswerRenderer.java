package com.sahr.agent;

import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.SahrAnnotationVocabulary;

import java.util.Locale;

import simplenlg.features.Feature;
import simplenlg.features.NumberAgreement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.PPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

final class AnswerRenderer {
    interface DisplayFormatter {
        String localName(String predicate);

        Boolean booleanConcept(SymbolId id);

        String normalizeTypeToken(String raw);
    }

    private final DisplayFormatter formatter;
    private final OntologyAnnotationResolver resolver;
    private final NLGFactory nlgFactory;
    private final Realiser realiser;

    AnswerRenderer(DisplayFormatter formatter, OntologyAnnotationResolver resolver) {
        this.formatter = formatter;
        this.resolver = resolver;
        Lexicon lexicon = Lexicon.getDefaultLexicon();
        this.nlgFactory = new NLGFactory(lexicon);
        this.realiser = new Realiser(lexicon);
    }

    String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect) {
        String predicate = formatter.localName(assertion.predicate());
        TemplateSpec template = resolveTemplateSpec(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE);
        if ("causedby".equals(predicate)) {
            return formatAssertionSentence(assertion, effect, cause, template);
        }
        return formatAssertionSentence(assertion, cause, effect, template);
    }

    String formatRuleSentence(RuleAssertion rule) {
        RelationAssertion antecedent = rule.antecedent();
        RelationAssertion consequent = rule.consequent();
        String consequentPredicate = formatter.localName(consequent.predicate());
        if ("backupfor".equals(consequentPredicate) || "backup_for".equals(consequentPredicate)) {
            String subject = displayValue(consequent.subject());
            String object = normalizeBackupTarget(displayValue(consequent.object()));
            String antecedentText = normalizePluralAgreement(formatAssertionClause(antecedent));
            return subject + " can serve as a backup for " + object + " when " + antecedentText + ".";
        }
        return "If " + formatAssertionClause(antecedent) + ", then " + formatAssertionClause(consequent) + ".";
    }

    String formatAssertionSentence(RelationAssertion assertion) {
        return ensureSentenceTerminal(formatAssertionClause(assertion));
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
            TemplateSpec template = booleanValue
                    ? resolveTemplateSpec(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE_TRUE)
                    : resolveTemplateSpec(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE_FALSE);
            if (template != null) {
                return formatAssertionClause(assertion, subject, object, booleanValue, template);
            }
        } else {
            TemplateSpec template = resolveTemplateSpec(assertion.predicate(), SahrAnnotationVocabulary.ANSWER_TEMPLATE);
            if (template != null) {
                return formatAssertionClause(assertion, subject, object, null, template);
            }
        }
        if (booleanValue != null) {
            if ("fail".equals(predicate)) {
                return renderClause(subjectText, null, new TemplateSpec("fail"), booleanValue);
            }
            if ("operate".equals(predicate) || "function".equals(predicate)
                    || "work".equals(predicate) || "respond".equals(predicate)
                    || "stop_responding".equals(predicate) || "stop".equals(predicate)) {
                return renderClause(subjectText, null, new TemplateSpec("operate"), booleanValue);
            }
            if ("control".equals(predicate) && !booleanValue) {
                return "Loss of " + subjectText;
            }
            if ("become_unstable".equals(predicate) || "unstable".equals(predicate)) {
                return renderClause(subjectText, booleanValue ? "unstable" : "stable", new TemplateSpec("become"), booleanValue);
            }
            TemplateSpec fallback = templateForPredicate(displayPredicate(assertion.predicate()));
            return renderClause(subjectText, null, fallback, booleanValue);
        }
        if ("backupfor".equals(predicate) || "backup_for".equals(predicate)) {
            TemplateSpec template = new TemplateSpec("be")
                    .withObject("backup")
                    .withPreposition("for");
            return renderClause(subjectText, displayValue(object), template, null);
        }
        if ("poweredby".equals(predicate) || "powered_by".equals(predicate)) {
            TemplateSpec template = new TemplateSpec("power")
                    .withVoice(Voice.PASSIVE)
                    .withPreposition("by");
            return renderClause(subjectText, displayValue(object), template, null);
        }
        if ("restore".equals(predicate)) {
            return renderClause(subjectText, displayValue(object), new TemplateSpec("restore"), null);
        }
        return renderClause(subjectText, displayValue(object), templateForPredicate(displayPredicate(assertion.predicate())), null);
    }

    private String normalizeBackupTarget(String target) {
        if (target == null || target.isBlank()) {
            return target;
        }
        String normalized = target.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("backup ")) {
            return target.substring("backup ".length()).trim();
        }
        return target;
    }

    private String normalizePluralAgreement(String clause) {
        if (clause == null) {
            return null;
        }
        String trimmed = clause;
        if (trimmed.endsWith(" fails..")) {
            return trimmed.substring(0, trimmed.length() - " fails..".length()) + " fail..";
        }
        if (trimmed.endsWith(" fails.")) {
            return trimmed.substring(0, trimmed.length() - " fails.".length()) + " fail.";
        }
        if (trimmed.endsWith(" fails")) {
            return trimmed.substring(0, trimmed.length() - " fails".length()) + " fail";
        }
        return trimmed;
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

    private String formatAssertionSentence(RelationAssertion assertion, SymbolId subject, SymbolId object, TemplateSpec template) {
        return ensureSentenceTerminal(formatAssertionClause(assertion, subject, object, null, template));
    }

    private String formatAssertionClause(RelationAssertion assertion,
                                         SymbolId subject,
                                         SymbolId object,
                                         Boolean booleanValue,
                                         TemplateSpec template) {
        if (assertion == null || subject == null) {
            return "unknown";
        }
        String subjectText = displayValue(subject);
        String objectText = object == null ? null : displayValue(object);
        TemplateSpec spec = template == null ? templateForPredicate(displayPredicate(assertion.predicate())) : template;
        String normalizedObject = booleanValue != null ? null : objectText;
        return renderClause(subjectText, normalizedObject, spec, booleanValue);
    }

    private String renderClause(String subjectText, String objectText, TemplateSpec spec, Boolean booleanValue) {
        if (subjectText == null || subjectText.isBlank()) {
            return "unknown";
        }
        String verb = spec.verb;
        if (verb == null || verb.isBlank()) {
            return subjectText;
        }
        String cleanedSubject = subjectText;
        if (cleanedSubject != null && cleanedSubject.toLowerCase(Locale.ROOT).startsWith("by ")) {
            cleanedSubject = cleanedSubject.substring(3).trim();
        }
        SPhraseSpec clause = nlgFactory.createClause();
        if (isPluralToken(cleanedSubject)) {
            clause.setFeature(Feature.NUMBER, NumberAgreement.PLURAL);
        }
        clause.setSubject(nounPhrase(cleanedSubject));
        clause.setVerb(verb);
        if (spec.voice == Voice.PASSIVE) {
            clause.setFeature(Feature.PASSIVE, true);
        }
        boolean negated = spec.negated || (booleanValue != null && !booleanValue);
        if (negated) {
            clause.setFeature(Feature.NEGATED, true);
        }

        String fixedObject = spec.object;
        String mainObject = fixedObject != null ? fixedObject : objectText;
        if (mainObject != null && !mainObject.isBlank() && (spec.preposition == null || fixedObject != null)) {
            clause.setObject(nounPhrase(mainObject));
        }
        if (spec.preposition != null && objectText != null && !objectText.isBlank()) {
            PPPhraseSpec pp = nlgFactory.createPrepositionPhrase();
            pp.setPreposition(spec.preposition);
            pp.addComplement(nounPhrase(objectText));
            clause.addComplement(pp);
        }
        String realised = realiser.realise(clause).getRealisation();
        if (realised == null || realised.isBlank()) {
            return subjectText;
        }
        return realised.trim();
    }

    private NPPhraseSpec nounPhrase(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return nlgFactory.createNounPhrase("unknown");
        }
        NPPhraseSpec phrase = nlgFactory.createNounPhrase(normalized);
        if ("backup".equalsIgnoreCase(normalized)) {
            phrase.setDeterminer("a");
        }
        return phrase;
    }

    private TemplateSpec templateForPredicate(String predicateText) {
        if (predicateText == null || predicateText.isBlank()) {
            return new TemplateSpec("be");
        }
        String normalized = predicateText.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("powered by")) {
            return new TemplateSpec("power")
                    .withVoice(Voice.PASSIVE)
                    .withPreposition("by");
        }
        return new TemplateSpec(baseVerb(predicateText));
    }

    private String baseVerb(String verb) {
        if (verb == null || verb.isBlank()) {
            return verb;
        }
        String trimmed = verb.trim();
        if (trimmed.contains(" ")) {
            return trimmed;
        }
        if (trimmed.endsWith("s") && trimmed.length() > 1 && !trimmed.endsWith("ss")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isPluralToken(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        if (normalized.contains(" and ")) {
            return true;
        }
        String[] tokens = normalized.split("\\s+");
        String last = tokens[tokens.length - 1];
        return last.endsWith("s") && !last.endsWith("ss");
    }


    private TemplateSpec resolveTemplateSpec(String predicate, String annotationIri) {
        String template = resolveTemplate(predicate, annotationIri);
        if (template == null || template.isBlank()) {
            return null;
        }
        return TemplateSpec.parse(template);
    }

    private String resolveTemplate(String predicate, String annotationIri) {
        if (resolver == null) {
            return null;
        }
        return resolver.resolveObjectPropertyIri(predicate)
                .flatMap(iri -> resolver.annotationValue(iri, annotationIri))
                .orElse(null);
    }

    private String ensureSentenceTerminal(String text) {
        if (text == null || text.isBlank()) {
            return "unknown.";
        }
        String trimmed = text.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }

    private enum Voice {
        ACTIVE,
        PASSIVE
    }

    private static final class TemplateSpec {
        private final String verb;
        private String object;
        private String preposition;
        private Voice voice = Voice.ACTIVE;
        private boolean negated;

        private TemplateSpec(String verb) {
            this.verb = verb;
        }

        private TemplateSpec withObject(String object) {
            this.object = object;
            return this;
        }

        private TemplateSpec withPreposition(String preposition) {
            this.preposition = preposition;
            return this;
        }

        private TemplateSpec withVoice(Voice voice) {
            if (voice != null) {
                this.voice = voice;
            }
            return this;
        }

        private static TemplateSpec parse(String template) {
            String trimmed = template == null ? "" : template.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            String[] tokens = trimmed.split(";");
            TemplateSpec spec = null;
            for (String token : tokens) {
                String part = token.trim();
                if (part.isBlank()) {
                    continue;
                }
                String[] kv = part.split(":", 2);
                if (kv.length == 1) {
                    if (spec == null) {
                        spec = new TemplateSpec(part);
                    }
                    continue;
                }
                String key = kv[0].trim().toLowerCase(Locale.ROOT);
                String value = kv[1].trim();
                if (spec == null && "verb".equals(key)) {
                    spec = new TemplateSpec(value);
                    continue;
                }
                if (spec == null) {
                    continue;
                }
                switch (key) {
                    case "verb" -> spec = new TemplateSpec(value);
                    case "object" -> spec.object = value;
                    case "prep", "preposition" -> spec.preposition = value;
                    case "voice" -> spec.voice = "passive".equalsIgnoreCase(value) ? Voice.PASSIVE : Voice.ACTIVE;
                    case "negated" -> spec.negated = Boolean.parseBoolean(value);
                    default -> {
                    }
                }
            }
            return spec;
        }
    }
}
