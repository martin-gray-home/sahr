package com.sahr.nlp;

import java.util.List;
import java.util.Objects;

public final class LanguageGraph {
    public enum QuestionShape {
        WH_PREPOSITION_LEADING,
        WH_PREPOSITION_TRAILING,
        UNKNOWN
    }

    private final String utterance;
    private final List<String> tokens;
    private final String whToken;
    private final String copulaToken;
    private final String relationToken;
    private final List<String> anchorTokens;
    private final String anchorToken;
    private final String anchorModifier;
    private final QuestionShape questionShape;

    LanguageGraph(String utterance,
                  List<String> tokens,
                  String whToken,
                  String copulaToken,
                  String relationToken,
                  List<String> anchorTokens,
                  String anchorToken,
                  String anchorModifier,
                  QuestionShape questionShape) {
        this.utterance = Objects.requireNonNull(utterance, "utterance");
        this.tokens = List.copyOf(tokens);
        this.whToken = whToken;
        this.copulaToken = copulaToken;
        this.relationToken = relationToken;
        this.anchorTokens = List.copyOf(anchorTokens);
        this.anchorToken = anchorToken;
        this.anchorModifier = anchorModifier;
        this.questionShape = Objects.requireNonNull(questionShape, "questionShape");
    }

    public String utterance() {
        return utterance;
    }

    public List<String> tokens() {
        return tokens;
    }

    public String whToken() {
        return whToken;
    }

    public String copulaToken() {
        return copulaToken;
    }

    public String relationToken() {
        return relationToken;
    }

    public List<String> anchorTokens() {
        return anchorTokens;
    }

    public String anchorToken() {
        return anchorToken;
    }

    public String anchorModifier() {
        return anchorModifier;
    }

    public QuestionShape questionShape() {
        return questionShape;
    }
}
