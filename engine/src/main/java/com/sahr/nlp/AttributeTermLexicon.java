package com.sahr.nlp;

import java.util.Set;

public final class AttributeTermLexicon {
    private static final Set<String> PROPERTY_TERMS = Set.of(
            "red",
            "blue",
            "green",
            "black",
            "white",
            "yellow",
            "orange",
            "purple",
            "pink",
            "brown",
            "gray",
            "grey",
            "tall",
            "short",
            "heavy",
            "light",
            "big",
            "small",
            "large",
            "tiny",
            "hot",
            "cold",
            "warm",
            "cool",
            "loud",
            "quiet",
            "bright",
            "dark",
            "clean",
            "dirty",
            "wet",
            "dry",
            "open",
            "closed",
            "full",
            "empty",
            "fast",
            "slow",
            "old",
            "new",
            "young",
            "strong",
            "weak"
    );

    private AttributeTermLexicon() {
    }

    public static boolean isPropertyTerm(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        return PROPERTY_TERMS.contains(term);
    }
}
