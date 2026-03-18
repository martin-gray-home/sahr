package com.sahr.core;

import com.sahr.nlp.SimpleQueryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryNormalizerTest {
    private final SimpleQueryParser parser = new SimpleQueryParser();
    private final QueryNormalizer normalizer = new QueryNormalizer();

    @Test
    void normalizesWhPrepositionObjectIntoSubjectSlot() {
        QueryGoal query = parser.parse("What is on the man");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertNull(frame.subject());
        assertEquals("on", frame.predicate());
        assertEquals("man", frame.object());
        assertEquals(QueryFrame.TargetSlot.SUBJECT, frame.targetSlot());
    }

    @Test
    void normalizesWhPrepositionSubjectIntoObjectSlot() {
        QueryGoal query = parser.parse("What is the hat on");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertEquals("hat", frame.subject());
        assertEquals("on", frame.predicate());
        assertNull(frame.object());
        assertEquals(QueryFrame.TargetSlot.OBJECT, frame.targetSlot());
    }

    @Test
    void normalizesWhUnderQueryIntoSubjectSlot() {
        QueryGoal query = parser.parse("What is under the hat");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertNull(frame.subject());
        assertEquals("under", frame.predicate());
        assertEquals("hat", frame.object());
        assertEquals(QueryFrame.TargetSlot.SUBJECT, frame.targetSlot());
    }

    @Test
    void normalizesAttributeWhIntoRelationFrame() {
        QueryGoal query = parser.parse("What is green");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertNull(frame.subject());
        assertEquals("hasAttribute", frame.predicate());
        assertEquals("green", frame.object());
        assertEquals(QueryFrame.TargetSlot.SUBJECT, frame.targetSlot());
    }

    @Test
    void normalizesAttributeWhIntoRelationFrameForPropertyTerm() {
        QueryGoal query = parser.parse("What is tall");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertNull(frame.subject());
        assertEquals("hasAttribute", frame.predicate());
        assertEquals("tall", frame.object());
        assertEquals(QueryFrame.TargetSlot.SUBJECT, frame.targetSlot());
    }

    @Test
    void doesNotPromoteNounToAttributeFrame() {
        QueryGoal query = parser.parse("What is chair");
        QueryFrame frame = normalizer.normalize(query, QueryOperator.RETRIEVE, null);

        assertEquals(QueryOperator.RETRIEVE, frame.operator());
        org.junit.jupiter.api.Assertions.assertNotEquals("hasAttribute", frame.predicate(),
                "Expected non-attribute predicate for noun term");
    }
}
