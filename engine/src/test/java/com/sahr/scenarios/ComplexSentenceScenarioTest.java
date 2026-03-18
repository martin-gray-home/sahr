package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.heads.AttributeQueryHead;
import org.junit.jupiter.api.Test;
import java.util.Set;
import com.sahr.support.SahrTestAgentFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexSentenceScenarioTest {
    @Test
    void answersQuestionsFromCompoundSentence() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph, new AttributeQueryHead());

        assertEquals("Assertion recorded.", agent.handle("The man and the boy in the room are with the red dog."));

        assertTrue(matchesLocation("entity:man", "entity:room", agent.handle("Where is the man")));
        assertTrue(matchesLocation("entity:boy", "entity:room", agent.handle("Where is the boy")));
        assertEquals("No candidates produced.", agent.handle("Where is the dog"));

        String whoWithMan = agent.handle("Who is with the man");
        assertTrue(Set.of("entity:dog", "entity:red_dog", "entity:red_dog, entity:dog", "entity:dog, entity:red_dog")
                .contains(whoWithMan));
        String whoWithBoy = agent.handle("Who is with the boy");
        assertTrue(Set.of("entity:dog", "entity:red_dog", "entity:red_dog, entity:dog", "entity:dog, entity:red_dog")
                .contains(whoWithBoy));
        String whatManWith = agent.handle("What is the man with");
        assertTrue(Set.of("entity:dog", "entity:red_dog", "entity:red_dog, entity:dog", "entity:dog, entity:red_dog")
                .contains(whatManWith));

        String whoIsWithDog = agent.handle("Who is with the dog");
        assertTrue(Set.of("entity:man", "entity:boy", "entity:man, entity:boy").contains(whoIsWithDog));

        assertTrue(agent.handle("Is the man with the dog").startsWith("Yes,"));
        assertTrue(agent.handle("Is the boy with the dog").startsWith("Yes,"));

        assertEquals("red", agent.handle("What color is the dog"));
        String whoIsWithRedDog = agent.handle("Who is with the red dog");
        assertTrue(Set.of("entity:man", "entity:boy", "entity:man, entity:boy").contains(whoIsWithRedDog));
        assertEquals("2", agent.handle("How many people are in the room"));
        assertEquals("2", agent.handle("How many people with the dog"));
    }

    private boolean matchesLocation(String subject, String object, String answer) {
        return (subject + " in " + object).equals(answer);
    }
}
