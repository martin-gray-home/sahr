package com.sahr.nlp;

import java.util.Locale;
import java.util.Optional;

public final class RuleParser {
    private final StatementParser statementParser;

    public RuleParser(StatementParser statementParser) {
        this.statementParser = statementParser == null ? new StatementParser(true) : statementParser;
    }

    public Optional<RuleStatement> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("if ")) {
            ClauseSplit trailing = splitTrailingConditional(trimmed);
            if (trailing == null) {
                return Optional.empty();
            }
            Optional<Statement> antecedent = statementParser.parse(trailing.antecedent);
            Optional<Statement> consequent = statementParser.parse(trailing.consequent);
            if (antecedent.isEmpty() || consequent.isEmpty()) {
                String normalizedAntecedent = stripModals(trailing.antecedent);
                String normalizedConsequent = stripModals(trailing.consequent);
                if (!normalizedAntecedent.equals(trailing.antecedent)) {
                    antecedent = antecedent.isPresent() ? antecedent : statementParser.parse(normalizedAntecedent);
                }
                if (!normalizedConsequent.equals(trailing.consequent)) {
                    consequent = consequent.isPresent() ? consequent : statementParser.parse(normalizedConsequent);
                }
            }
            if (antecedent.isEmpty() || consequent.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RuleStatement(antecedent.get(), consequent.get(), 0.8));
        }

        ClauseSplit split = splitConditional(trimmed);
        if (split == null) {
            return Optional.empty();
        }
        Optional<Statement> antecedent = statementParser.parse(split.antecedent);
        Optional<Statement> consequent = statementParser.parse(split.consequent);
        if (antecedent.isEmpty() || consequent.isEmpty()) {
            String normalizedAntecedent = stripModals(split.antecedent);
            String normalizedConsequent = stripModals(split.consequent);
            if (!normalizedAntecedent.equals(split.antecedent)) {
                antecedent = antecedent.isPresent() ? antecedent : statementParser.parse(normalizedAntecedent);
            }
            if (!normalizedConsequent.equals(split.consequent)) {
                consequent = consequent.isPresent() ? consequent : statementParser.parse(normalizedConsequent);
            }
        }
        if (antecedent.isEmpty() || consequent.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuleStatement(antecedent.get(), consequent.get(), 0.8));
    }

    private ClauseSplit splitConditional(String input) {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int thenIdx = lower.indexOf(" then ");
        if (thenIdx >= 0) {
            String antecedent = trimmed.substring(3, thenIdx).trim();
            String consequent = trimmed.substring(thenIdx + 6).trim();
            if (!antecedent.isEmpty() && !consequent.isEmpty()) {
                return new ClauseSplit(antecedent, consequent);
            }
        }
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 3) {
            String antecedent = trimmed.substring(3, commaIdx).trim();
            String consequent = trimmed.substring(commaIdx + 1).trim();
            if (!antecedent.isEmpty() && !consequent.isEmpty()) {
                return new ClauseSplit(antecedent, consequent);
            }
        }
        return null;
    }

    private ClauseSplit splitTrailingConditional(String input) {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int ifIdx = lower.indexOf(" if ");
        if (ifIdx <= 0 || ifIdx + 4 >= trimmed.length()) {
            return null;
        }
        String consequent = trimmed.substring(0, ifIdx).trim();
        String antecedent = trimmed.substring(ifIdx + 4).trim();
        if (antecedent.isEmpty() || consequent.isEmpty()) {
            return null;
        }
        return new ClauseSplit(antecedent, consequent);
    }

    private static final class ClauseSplit {
        private final String antecedent;
        private final String consequent;

        private ClauseSplit(String antecedent, String consequent) {
            this.antecedent = antecedent;
            this.consequent = consequent;
        }
    }

    private String stripModals(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String normalized = input.replaceAll("(?i)\\b(may|might|can|could|would|should|must|will|shall)\\b", "");
        return normalized.replaceAll("\\s{2,}", " ").trim();
    }
}
