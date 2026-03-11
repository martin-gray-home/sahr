package com.sahr.nlp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InputFeatureExtractorTest {
    @Test
    void extractsConditionalAndWhMarkers() {
        InputFeatures features = InputFeatureExtractor.extract(
                "If the electrical bus voltage drops, which systems stop?");

        assertTrue(features.has("has_if"));
        assertTrue(features.has("has_conditional"));
        assertTrue(features.has("has_wh"));
        assertTrue(features.has("has_which"));
        assertTrue(features.has("has_question_mark"));
    }
}
