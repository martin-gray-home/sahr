package com.sahr.nlp;

import com.sahr.core.RuleAtom;
import com.sahr.core.RuleFrame;
import com.sahr.core.RuleTerm;
import edu.stanford.nlp.process.Morphology;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuantifiedRuleParser {
    private static final Pattern ALL_IN_ARE_PATTERN = Pattern.compile(
            "^all\\s+([a-z0-9_]+)\\s+in\\s+(?:the\\s+)?([a-z0-9_]+)\\s+are\\s+([a-z0-9_]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Morphology MORPHOLOGY = new Morphology();

    private static final String PREDICATE_TYPE = "rdf:type";
    private static final String PREDICATE_IN = "in";
    private static final String PREDICATE_ATTRIBUTE = "hasAttribute";

    public Optional<RuleFrame> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String normalized = trimmed.replaceAll("[\\.!?]+$", "").trim();
        Matcher matcher = ALL_IN_ARE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String subjectPlural = matcher.group(1);
        String containerRaw = matcher.group(2);
        String attributeRaw = matcher.group(3);
        if (subjectPlural == null || containerRaw == null || attributeRaw == null) {
            return Optional.empty();
        }
        String subject = singularize(subjectPlural);
        String container = normalizeToken(containerRaw);
        String attribute = normalizeToken(attributeRaw);
        if (subject.isBlank() || container.isBlank() || attribute.isBlank()) {
            return Optional.empty();
        }
        String variable = "x";
        RuleTerm var = RuleTerm.variable(variable);
        RuleAtom typeAtom = new RuleAtom(var, PREDICATE_TYPE, RuleTerm.constant("concept:" + subject));
        RuleAtom inAtom = new RuleAtom(var, PREDICATE_IN, RuleTerm.constant("entity:" + container));
        RuleAtom consequent = new RuleAtom(var, PREDICATE_ATTRIBUTE, RuleTerm.constant("concept:" + attribute));
        return Optional.of(new RuleFrame(variable, List.of(typeAtom, inAtom), consequent, 0.8));
    }

    private String singularize(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String lemma = MORPHOLOGY.lemma(token.toLowerCase(Locale.ROOT), "NN");
        String lowered = token.toLowerCase(Locale.ROOT);
        if (lemma != null && !lemma.isBlank()) {
            if (!lemma.equals(lowered)) {
                return lemma;
            }
        }
        if (lowered.endsWith("s") && lowered.length() > 1) {
            return lowered.substring(0, lowered.length() - 1);
        }
        return lowered;
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        return token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "").trim();
    }
}
