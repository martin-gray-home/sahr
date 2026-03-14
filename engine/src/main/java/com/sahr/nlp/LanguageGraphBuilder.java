package com.sahr.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LanguageGraphBuilder {
    private static final Set<String> WH_TOKENS = Set.of("who", "what", "where", "when", "why", "how", "which");
    private static final Set<String> COPULA_TOKENS = Set.of("is", "are", "was", "were");
    private static final Set<String> PREPOSITION_RELATIONS = Set.of("on", "under", "above", "below", "with", "in", "inside", "opposite");
    private static final Set<String> COLOCATION_SYNONYMS = Set.of("near", "beside", "alongside", "next");
    private static final Set<String> COLOR_MODIFIERS = Set.of("red", "blue", "green", "black", "white");
    private static final Set<String> DETERMINERS = Set.of("the", "a", "an");

    private final boolean ontologyDriven;

    public LanguageGraphBuilder(boolean ontologyDriven) {
        this.ontologyDriven = ontologyDriven;
    }

    public LanguageGraph build(String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) {
            return new LanguageGraph(normalized, List.of(), null, null, null, List.of(), null, null,
                    LanguageGraph.QuestionShape.UNKNOWN);
        }

        String whToken = WH_TOKENS.contains(tokens.get(0)) ? tokens.get(0) : null;
        int searchStart = whToken == null ? 0 : 1;
        int copulaIndex = findTokenIndex(tokens, COPULA_TOKENS, searchStart);
        int relationIndex = copulaIndex >= 0
                ? findTokenIndex(tokens, PREPOSITION_RELATIONS, copulaIndex + 1)
                : -1;
        int trailingRelationIndex = tokens.isEmpty() ? -1 : tokens.size() - 1;
        if (trailingRelationIndex >= 0 && !PREPOSITION_RELATIONS.contains(tokens.get(trailingRelationIndex))) {
            trailingRelationIndex = -1;
        }

        String copulaToken = copulaIndex >= 0 ? tokens.get(copulaIndex) : null;
        String relationToken = relationIndex >= 0 ? tokens.get(relationIndex) : null;

        LanguageGraph.QuestionShape shape = LanguageGraph.QuestionShape.UNKNOWN;
        List<String> anchorTokens = List.of();

        if (whToken != null && relationToken != null) {
            anchorTokens = extractAnchorTokens(tokens, relationIndex + 1, tokens.size());
            String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
            if (anchorToken != null && !anchorToken.isBlank()) {
                shape = LanguageGraph.QuestionShape.WH_PREPOSITION_LEADING;
            }
        }

        if (shape == LanguageGraph.QuestionShape.UNKNOWN && whToken != null && trailingRelationIndex >= 0 && copulaIndex >= 0) {
            relationToken = tokens.get(trailingRelationIndex);
            anchorTokens = extractAnchorTokens(tokens, copulaIndex + 1, trailingRelationIndex);
            String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
            if (anchorToken != null && !anchorToken.isBlank() && !containsGerund(anchorTokens)) {
                shape = LanguageGraph.QuestionShape.WH_PREPOSITION_TRAILING;
            }
        }

        if (shape == LanguageGraph.QuestionShape.UNKNOWN && whToken != null && copulaIndex >= 0) {
            int verbIndex = findGerundIndex(tokens, copulaIndex + 1);
            if (verbIndex >= 0 && verbIndex + 1 < tokens.size() && !PREPOSITION_RELATIONS.contains(tokens.get(verbIndex + 1))) {
                relationToken = tokens.get(verbIndex);
                anchorTokens = extractAnchorTokens(tokens, verbIndex + 1, tokens.size());
                String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
                if (anchorToken != null && !anchorToken.isBlank()) {
                    shape = LanguageGraph.QuestionShape.WH_VERB_OBJECT;
                }
            }
        }

        if (shape == LanguageGraph.QuestionShape.UNKNOWN && whToken != null && copulaIndex >= 0) {
            int verbIndex = findTrailingGerundIndex(tokens);
            if (verbIndex > copulaIndex) {
                relationToken = tokens.get(verbIndex);
                anchorTokens = extractAnchorTokens(tokens, copulaIndex + 1, verbIndex);
                String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
                if (anchorToken != null && !anchorToken.isBlank() && !containsGerund(anchorTokens)) {
                    shape = LanguageGraph.QuestionShape.WH_OBJECT_VERB;
                }
            }
        }

        String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
        String anchorModifier = anchorTokens.size() > 1 && COLOR_MODIFIERS.contains(anchorTokens.get(0))
                ? anchorTokens.get(0)
                : null;

        return new LanguageGraph(
                normalized,
                tokens,
                whToken,
                copulaToken,
                relationToken,
                anchorTokens,
                anchorToken,
                anchorModifier,
                shape
        );
    }

    private int findTokenIndex(List<String> tokens, Set<String> candidates, int start) {
        for (int i = Math.max(0, start); i < tokens.size(); i++) {
            if (candidates.contains(tokens.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<String> extractAnchorTokens(List<String> tokens, int start, int end) {
        List<String> anchor = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String token = tokens.get(i);
            if (DETERMINERS.contains(token)) {
                continue;
            }
            anchor.add(token);
        }
        return anchor;
    }

    private boolean containsGerund(List<String> tokens) {
        for (String token : tokens) {
            if (isGerund(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGerund(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        return trimmed.length() > 4 && trimmed.endsWith("ing");
    }

    private int findGerundIndex(List<String> tokens, int start) {
        for (int i = Math.max(0, start); i < tokens.size(); i++) {
            if (isGerund(tokens.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findTrailingGerundIndex(List<String> tokens) {
        if (tokens.isEmpty()) {
            return -1;
        }
        int lastIndex = tokens.size() - 1;
        if (PREPOSITION_RELATIONS.contains(tokens.get(lastIndex))) {
            return -1;
        }
        if (isGerund(tokens.get(lastIndex))) {
            return lastIndex;
        }
        return -1;
    }

    private List<String> tokenize(String normalized) {
        String cleaned = normalized.replaceAll("[^a-z0-9\\s]", " ").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> tokens = List.of(cleaned.split("\\s+"));
        return normalizeColocationSynonyms(tokens);
    }

    private List<String> normalizeColocationSynonyms(List<String> tokens) {
        if (ontologyDriven) {
            return tokens;
        }
        if (tokens.isEmpty()) {
            return tokens;
        }
        List<String> normalized = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("next".equals(token) && i + 1 < tokens.size() && "to".equals(tokens.get(i + 1))) {
                normalized.add("with");
                i++;
                continue;
            }
            if (COLOCATION_SYNONYMS.contains(token)) {
                normalized.add("with");
                continue;
            }
            normalized.add(token);
        }
        return normalized;
    }
}
