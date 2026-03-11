package com.sahr.nlp;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InputFeatureExtractor {
    private static final Set<String> WH_TOKENS = Set.of("who", "what", "where", "when", "why", "how", "which");
    private static final Set<String> AUX_TOKENS = Set.of("is", "are", "was", "were", "do", "does", "did", "can", "could", "should", "would", "will", "may", "might", "must", "shall");
    private static final Set<String> MODAL_TOKENS = Set.of("can", "could", "should", "would", "may", "might", "must", "will", "shall");

    private InputFeatureExtractor() {
    }

    public static InputFeatures extract(String input) {
        Set<String> features = new HashSet<>();
        if (input == null) {
            return new InputFeatures(features, "", List.of());
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new InputFeatures(features, "", List.of());
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        List<String> tokens = List.of(lower.split("\\s+"));

        if (trimmed.contains("?")) {
            features.add("has_question_mark");
        }
        if (tokens.contains("if")) {
            features.add("has_if");
        }
        if (tokens.contains("then")) {
            features.add("has_then");
        }
        if (trimmed.contains(",")) {
            features.add("has_comma");
        }
        for (String token : tokens) {
            if (WH_TOKENS.contains(token)) {
                features.add("has_wh");
                features.add("has_" + token);
            }
            if (AUX_TOKENS.contains(token)) {
                features.add("has_aux");
            }
            if (MODAL_TOKENS.contains(token)) {
                features.add("has_modal");
            }
            if ("explain".equals(token)) {
                features.add("has_explain");
            }
        }
        if (features.contains("has_if") && (features.contains("has_then") || features.contains("has_comma"))) {
            features.add("has_conditional");
        }

        return new InputFeatures(features, trimmed, tokens);
    }
}
