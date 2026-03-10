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
    void bindsEntityTypeFromWhereWas() {
        QueryGoal query = parser.parse("Where was the man");

        assertEquals(QueryGoal.Type.WHERE, query.type());
        assertEquals("man", query.entityType());
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
        assertEquals("man", query.object());
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
    void parsesRelationQueryFromWhSubjectNoObject() {
        QueryGoal query = parser.parse("Who was eating");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("eat", query.predicate());
        assertEquals("person", query.expectedType());
        assertNull(query.subject());
        assertNull(query.object());
    }

    @Test
    void lemmatizesVerbInWhSubjectNoObject() {
        QueryGoal query = parser.parse("Who was eaten");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("eat", query.predicate());
        assertEquals("person", query.expectedType());
        assertNull(query.subject());
        assertNull(query.object());
    }

    @Test
    void lemmatizesVerbInWhPrepositionObjectPattern() {
        QueryGoal query = parser.parse("What was the man sitting on");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("on", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesPassiveByWhSubjectQuery() {
        QueryGoal query = parser.parse("Who was eaten by the lion");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertNull(query.subject());
        assertEquals("eat", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesPassiveByWhObjectQuery() {
        QueryGoal query = parser.parse("What was the man eaten by");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertNull(query.object());
        assertEquals("eat", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesPassiveByWhSubjectWithPredicate() {
        QueryGoal query = parser.parse("What was worn by the man");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertNull(query.subject());
        assertEquals("wear", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesDativeRecipientQuery() {
        QueryGoal query = parser.parse("Who did the man give the book to");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("give", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesDativeObjectQuery() {
        QueryGoal query = parser.parse("What did the man give the boy");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("give", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesYesNoPassiveByQuery() {
        QueryGoal query = parser.parse("Was the hat worn by the man");

        assertEquals(QueryGoal.Type.YESNO, query.type());
        assertEquals("man", query.subject());
        assertEquals("hat", query.object());
        assertEquals("wear", query.predicate());
    }

    @Test
    void parsesRelationQueryFromUnderPattern() {
        QueryGoal query = parser.parse("What is under the hat");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("hat", query.object());
        assertEquals("under", query.predicate());
        assertEquals("entity", query.expectedType());
    }

    @Test
    void parsesRelationQueryFromWhPrepositionObjectPattern() {
        QueryGoal query = parser.parse("What is the man sitting on");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("man", query.subject());
        assertEquals("on", query.predicate());
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
    void parsesRelationQueryWithAdjectiveNounObject() {
        QueryGoal query = parser.parse("Who is in the red room");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("red_room", query.object());
        assertEquals("red", query.modifier());
        assertEquals("locatedIn", query.predicate());
        assertEquals("person", query.expectedType());
    }

    @Test
    void parsesRelationQueryWithCompoundObject() {
        QueryGoal query = parser.parse("Who is with the power bus");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("power_bus", query.subject());
        assertEquals("with", query.predicate());
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
    void parsesYesNoQueryWithoutObject() {
        QueryGoal query = parser.parse("Is the man sitting");

        assertEquals(QueryGoal.Type.YESNO, query.type());
        assertEquals("man", query.subject());
        assertEquals("sit", query.predicate());
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
    void parsesYesNoOppositeQuery() {
        QueryGoal query = parser.parse("Is the boy opposite the man");

        assertEquals(QueryGoal.Type.YESNO, query.type());
        assertEquals("boy", query.subject());
        assertEquals("man", query.object());
        assertEquals("opposite", query.predicate());
    }

    @Test
    void parsesWhatPowersQueryAsPassivePredicate() {
        QueryGoal query = parser.parse("What powers the transmitter");

        assertEquals(QueryGoal.Type.RELATION, query.type());
        assertEquals("transmitter", query.subject());
        assertEquals("poweredBy", query.predicate());
    }

    @Test
    void returnsUnknownForNonQuestions() {
        QueryGoal query = parser.parse("The cat is sleeping");

        assertEquals(QueryGoal.Type.UNKNOWN, query.type());
        assertNull(query.entityType());
    }

    @Test
    void ignoresRelativeWhoClauseInStatements() {
        QueryGoal query = parser.parse("the man and the boy were inside the room with the mother who was wearing a red hat");

        assertEquals(QueryGoal.Type.UNKNOWN, query.type());
    }
}
