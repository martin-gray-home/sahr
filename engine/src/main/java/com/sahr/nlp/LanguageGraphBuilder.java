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

        String copulaToken = copulaIndex >= 0 ? tokens.get(copulaIndex) : null;
        String relationToken = relationIndex >= 0 ? tokens.get(relationIndex) : null;

        List<String> anchorTokens = relationIndex >= 0
                ? extractAnchorTokens(tokens, relationIndex + 1)
                : List.of();
        String anchorToken = anchorTokens.isEmpty() ? null : String.join("_", anchorTokens);
        String anchorModifier = anchorTokens.size() > 1 && COLOR_MODIFIERS.contains(anchorTokens.get(0))
                ? anchorTokens.get(0)
                : null;

        LanguageGraph.QuestionShape shape = (whToken != null && relationToken != null && anchorToken != null && !anchorToken.isBlank())
                ? LanguageGraph.QuestionShape.WH_PREPOSITION
                : LanguageGraph.QuestionShape.UNKNOWN;

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

    private List<String> extractAnchorTokens(List<String> tokens, int start) {
        List<String> anchor = new ArrayList<>();
        for (int i = start; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (DETERMINERS.contains(token)) {
                continue;
            }
            anchor.add(token);
        }
        return anchor;
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
