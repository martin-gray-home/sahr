package com.sahr.reasoner;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiHeadReasoningTest {
    @Test
    void infersLocationViaWearThenAnswersWhere() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));
    }

    @Test
    void infersLocationViaWithThenAnswersWhere() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The woman is with the man"));
        assertEquals("entity:woman in entity:room", agent.handle("Where is the woman"));
    }

    @Test
    void answersYesNoAfterPropagation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);
        String under = "https://sahr.ai/ontology/relations#under";

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                under,
                new SymbolId("entity:man"),
                0.9
        ));

        assertEquals("Yes, the hat is under the man", agent.handle("Is the hat under the man"));
    }
}
