package com.sahr.nlp;

import com.sahr.core.QueryGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimpleQueryParserTest {
    private final SimpleQueryParser parser = new SimpleQueryParser();

    @Test
    void bindsEntityTypeFromWhereIs() {
        QueryGoal query = parser.parse("Where is the cat");

        assertEquals(QueryGoal.Type.WHERE, query.type());
        assertEquals("cat", query.entityType());
        assertEquals("concept:location", query.expectedRange());
    }

    @Test
    void returnsUnknownWhenWhereHasNoEntity() {
        QueryGoal query = parser.parse("where is it");

        assertEquals(QueryGoal.Type.UNKNOWN, query.type());
    }

    @Test
    void parsesRelationQueryFromWhObject() {
        QueryGoal query = parser.parse("What is the man wearing");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("wear", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWithPattern() {
        QueryGoal query = parser.parse("Who is with the man");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("with", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWithTrailingPattern() {
        QueryGoal query = parser.parse("Who is the man with");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("with", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromOnPattern() {
        QueryGoal query = parser.parse("What is on the man");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("on", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWhSubjectPattern() {
        QueryGoal query = parser.parse("Who is wearing a hat");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("hat", query.object());
        assertEquals("wear", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromUnderPattern() {
        QueryGoal query = parser.parse("What is under the hat");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("hat", query.subject());
        assertEquals("under", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWhoIsInPattern() {
        QueryGoal query = parser.parse("Who is in the house");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("house", query.object());
        assertEquals("locatedIn", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWhatIsInPattern() {
        QueryGoal query = parser.parse("What is in the house");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("house", query.object());
        assertEquals("locatedIn", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesYesNoQuery() {
        QueryGoal query = parser.parse("Is the man wearing a hat");

        assertEquals(QueryGoal.Type.YESNO, query.type());
        assertEquals("man", query.subject());
        assertEquals("hat", query.object());
        assertEquals("wear", query.predicate());
    }

    @Test
    void parsesYesNoPrepositionQuery() {
        QueryGoal query = parser.parse("Is the hat on the man");

        assertEquals(QueryGoal.Type.YESNO, query.type());
        assertEquals("hat", query.subject());
        assertEquals("man", query.object());
        assertEquals("on", query.predicate());
    }

    @Test
    void returnsUnknownForNonQuestions() {
        QueryGoal query = parser.parse("The cat is sleeping");

        assertEquals(QueryGoal.Type.UNKNOWN, query.type());
        assertNull(query.entityType());
    }
}
