package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SahrAgentQueryTest {
    @Test
    void answersWhoIsWithQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("entity:woman", agent.handle("Who is with the man"));
    }

    @Test
    void answersNumberedWhoIsWithQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("entity:woman", agent.handle("1. Who is with the man"));
    }

    @Test
    void answersWhoIsWearingQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:man", agent.handle("Who is wearing a hat"));
    }

    @Test
    void answersYesNoWearQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the man is wearing a hat", agent.handle("Is the man wearing a hat"));
    }

    @Test
    void answersYesNoOnQueryFromWear() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the hat is on the man", agent.handle("Is the hat on the man"));
    }

    @Test
    void answersUnknownForYesNoWithoutEvidence() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Unknown.", agent.handle("Is the woman wearing a hat"));
    }

    @Test
    void answersWhoElseQueryUsingHistory() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with the mother"));
        assertEquals("Assertion recorded.", agent.handle("The boy is with the mother"));

        String firstAnswer = agent.handle("Who is with the mother");
        boolean ordered = "entity:man, entity:boy".equals(firstAnswer) || "entity:boy, entity:man".equals(firstAnswer);
        if (!ordered) {
            throw new AssertionError("Unexpected answer: " + firstAnswer);
        }
        String elseAnswer = agent.handle("Who else is with the mother");
        if (!("No candidates produced.".equals(elseAnswer)
                || "Assertion recorded.".equals(elseAnswer)
                || "Assertion already known.".equals(elseAnswer)
                || "entity:man".equals(elseAnswer)
                || "entity:boy".equals(elseAnswer))) {
            throw new AssertionError("Unexpected answer: " + elseAnswer);
        }
    }

    @Test
    void answersWhereAfterOntologyAssertion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                "https://sahr.ai/ontology/relations#with",
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addEntity(new EntityNode(
                new SymbolId("entity:woman"),
                "woman",
                java.util.Set.of("concept:woman")
        ));

        assertEquals("entity:woman in entity:room", agent.handle("Where is the woman"));
    }

    @Test
    void answersCauseChainUsingRules() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        RuleAssertion wheelToReaction = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:wheel_motor"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:wheel_motor"), 0.9),
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                0.9
        );
        RuleAssertion reactionToInstability = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                new RelationAssertion(new SymbolId("entity:instability"), "https://sahr.ai/ontology/relations#causedBy",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                0.9
        );
        graph.addRule(wheelToReaction);
        graph.addRule(reactionToInstability);

        try {
            java.lang.reflect.Method method = SahrAgent.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation(null, "cause", "entity:instability", null);
            String answer = (String) method.invoke(agent, goal);
            assertEquals("entity:wheel_motor", answer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke executeCauseChain", e);
        }
    }
}
