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
            return Optional.empty();
        }

        ClauseSplit split = splitConditional(trimmed);
        if (split == null) {
            return Optional.empty();
        }
        Optional<Statement> antecedent = statementParser.parse(split.antecedent);
        Optional<Statement> consequent = statementParser.parse(split.consequent);
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

    private static final class ClauseSplit {
        private final String antecedent;
        private final String consequent;

        private ClauseSplit(String antecedent, String consequent) {
            this.antecedent = antecedent;
            this.consequent = consequent;
        }
    }
}
