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
    private static final Pattern CONDITIONAL_IN_ATTRIBUTE_PATTERN = Pattern.compile(
            "^if\\s+(?:a|an|the)\\s+([a-z0-9_]+)\\s+is\\s+in\\s+(?:the\\s+)?([a-z0-9_]+),?\\s+then\\s+(?:it|the\\s+([a-z0-9_]+))\\s+is\\s+([a-z0-9_]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITIONAL_CARRY_LOCATION_TRANSFER_PATTERN = Pattern.compile(
            "^if\\s+someone\\s+carries\\s+something\\s+and\\s+is\\s+in\\s+(?:a|the)\\s+place,?\\s+then\\s+(?:that|the)\\s+thing\\s+is\\s+in\\s+(?:that|the)\\s+place$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITIONAL_PARTOF_LOCATION_TRANSFER_PATTERN = Pattern.compile(
            "^if\\s+something\\s+is\\s+part\\s+of\\s+something\\s+and\\s+that\\s+thing\\s+is\\s+in\\s+(?:a|the)\\s+place,?\\s+then\\s+(?:the\\s+first\\s+thing|that\\s+part)\\s+is\\s+in\\s+(?:that|the)\\s+place$",
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
        Optional<RuleFrame> quantified = parseAllInAreRule(normalized);
        if (quantified.isPresent()) {
            return quantified;
        }
        Optional<RuleFrame> conditionalAttribute = parseConditionalInAttributeRule(normalized);
        if (conditionalAttribute.isPresent()) {
            return conditionalAttribute;
        }
        Optional<RuleFrame> carryTransfer = parseConditionalCarryLocationTransferRule(normalized);
        if (carryTransfer.isPresent()) {
            return carryTransfer;
        }
        return parseConditionalPartOfLocationTransferRule(normalized);
    }

    private Optional<RuleFrame> parseAllInAreRule(String normalized) {
        Matcher matcher = ALL_IN_ARE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String subject = singularize(matcher.group(1));
        String container = normalizeToken(matcher.group(2));
        String attribute = normalizeToken(matcher.group(3));
        return buildContainmentAttributeRule(subject, container, attribute);
    }

    private Optional<RuleFrame> parseConditionalInAttributeRule(String normalized) {
        Matcher matcher = CONDITIONAL_IN_ATTRIBUTE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String subject = singularize(matcher.group(1));
        String repeatedSubject = normalizeToken(matcher.group(3));
        if (!repeatedSubject.isBlank() && !repeatedSubject.equals(subject)) {
            return Optional.empty();
        }
        String container = normalizeToken(matcher.group(2));
        String attribute = normalizeToken(matcher.group(4));
        return buildContainmentAttributeRule(subject, container, attribute);
    }

    private Optional<RuleFrame> buildContainmentAttributeRule(String subject,
                                                              String container,
                                                              String attribute) {
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

    private Optional<RuleFrame> parseConditionalCarryLocationTransferRule(String normalized) {
        Matcher matcher = CONDITIONAL_CARRY_LOCATION_TRANSFER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        RuleTerm person = RuleTerm.variable("x");
        RuleTerm thing = RuleTerm.variable("y");
        RuleTerm place = RuleTerm.variable("z");
        RuleAtom carryAtom = new RuleAtom(person, "carry", thing);
        RuleAtom inAtom = new RuleAtom(person, PREDICATE_IN, place);
        RuleAtom consequent = new RuleAtom(thing, PREDICATE_IN, place);
        return Optional.of(new RuleFrame("x", List.of(carryAtom, inAtom), consequent, 0.8));
    }

    private Optional<RuleFrame> parseConditionalPartOfLocationTransferRule(String normalized) {
        Matcher matcher = CONDITIONAL_PARTOF_LOCATION_TRANSFER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        RuleTerm part = RuleTerm.variable("x");
        RuleTerm whole = RuleTerm.variable("y");
        RuleTerm place = RuleTerm.variable("z");
        RuleAtom partOfAtom = new RuleAtom(part, "partOf", whole);
        RuleAtom inAtom = new RuleAtom(whole, PREDICATE_IN, place);
        RuleAtom consequent = new RuleAtom(part, PREDICATE_IN, place);
        return Optional.of(new RuleFrame("x", List.of(partOfAtom, inAtom), consequent, 0.8));
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
