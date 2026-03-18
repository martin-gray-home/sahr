package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.heads.AttributeQueryHead;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexSentenceQueryScenarioTest {
    @Test
    void handlesComplexSentencesAndMixedQueries() {
        SahrAgent agent = newAgent();

        assertAnyOf("initial assertion", agent.handle("The red dog is in the box, the box is in the room, "
                        + "the red dog is with the black cat under the table in the room, "
                        + "the woman is on the chair in the room, "
                        + "and the man wearing a hat is with the woman"),
                "Assertion recorded.");

        assertAnyOf("Where is the dog", agent.handle("Where is the dog"),
                "entity:dog in entity:box",
                "entity:red_dog in entity:box");
        assertAnyOf("Where is the box", agent.handle("Where is the box"),
                "entity:box in entity:room");
        assertAnyOf("Where is the cat", agent.handle("Where is the cat"),
                "entity:cat in entity:room",
                "entity:black_cat in entity:room",
                "No candidates produced.");
        assertAnyOf("Where is the table", agent.handle("Where is the table"),
                "entity:table in entity:room",
                "entity:table in entity:box");
        assertAnyOf("Where is the woman", agent.handle("Where is the woman"),
                "entity:woman on entity:chair",
                "entity:woman in entity:room");
        assertAnyOf("Where is the man", agent.handle("Where is the man"),
                "entity:man under entity:hat",
                "entity:man on entity:hat",
                "entity:man wear entity:hat",
                "No candidates produced.");
        assertAnyOf("Where is the hat", agent.handle("Where is the hat"),
                "entity:hat on entity:man",
                "entity:hat under entity:man",
                "No candidates produced.");

        assertAnyOf("Who is with the dog", agent.handle("Who is with the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf("Who is the dog with", agent.handle("Who is the dog with"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf("What is with the dog", agent.handle("What is with the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf("Who is with the cat", agent.handle("Who is with the cat"),
                "entity:dog",
                "entity:red_dog",
                "entity:red_dog, entity:dog",
                "entity:dog, entity:red_dog");
        assertAnyOf("Who is with the woman", agent.handle("Who is with the woman"),
                "entity:man");
        assertAnyOf("Who is with the man", agent.handle("Who is with the man"),
                "entity:woman");
        assertAnyOf("Who is on the chair", agent.handle("Who is on the chair"),
                "entity:woman");
        assertAnyOf("What is under the table", agent.handle("What is under the table"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat",
                "entity:dog",
                "entity:red_dog",
                "entity:red_dog, entity:dog",
                "entity:dog, entity:red_dog");
        assertAnyOf("What is opposite the dog", agent.handle("What is opposite the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat",
                "No candidates produced.");

        assertAnyOf("What color is the dog", agent.handle("What color is the dog"),
                "red");
        assertAnyOf("What color is the cat", agent.handle("What color is the cat"),
                "black");

        assertAnyOf("Is the dog with the cat", agent.handle("Is the dog with the cat"),
                "Yes, the dog is with the cat",
                "Yes, the dog with the cat");
        assertAnyOf("Is the cat with the dog", agent.handle("Is the cat with the dog"),
                "Yes, the cat is with the dog",
                "Yes, the cat with the dog");
        assertAnyOf("Is the woman on the chair", agent.handle("Is the woman on the chair"),
                "Yes, the woman is on the chair",
                "Yes, the woman on the chair");
        assertAnyOf("Is the man with the woman", agent.handle("Is the man with the woman"),
                "Yes, the man is with the woman",
                "Yes, the man with the woman");
        assertAnyOf("Is the dog in the room", agent.handle("Is the dog in the room"),
                "Yes, the dog is in the room",
                "Yes, the dog in the room",
                "No candidates produced.");
        assertAnyOf("Is the dog in the box", agent.handle("Is the dog in the box"),
                "Yes, the dog is in the box",
                "Yes, the dog in the box",
                "No candidates produced.");

        assertAnyOf("How many people are in the room", agent.handle("How many people are in the room"),
                "1",
                "2",
                "No candidates produced.");
        assertAnyOf("How many people are with the woman", agent.handle("How many people are with the woman"),
                "1",
                "No candidates produced.");
        assertAnyOf("How many people are with the dog", agent.handle("How many people are with the dog"),
                "0",
                "No candidates produced.");
        assertAnyOf("How many people are on the chair", agent.handle("How many people are on the chair"),
                "1",
                "entity:woman",
                "No candidates produced.");
    }

    private SahrAgent newAgent() {
        return SahrTestAgentFactory.newAgent(new InMemoryKnowledgeBase(), new AttributeQueryHead());
    }

    private void assertAnyOf(String label, String actual, String... expected) {
        assertTrue(Set.of(expected).contains(actual),
                () -> "Unexpected answer for \"" + label + "\": " + actual);
    }
}
