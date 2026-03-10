package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.AttributeQueryHead;
import com.sahr.heads.ContainmentPropagationHead;
import com.sahr.heads.DependencyChainHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SubgoalExpansionHead;
import com.sahr.heads.SurfaceContactPropagationHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.support.HeadOntologyTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexSentenceQueryScenarioTest {
    @Test
    void handlesComplexSentencesAndMixedQueries() {
        SahrAgent agent = newAgent();

        assertAnyOf(agent.handle("The woman in the kitchen is with the man who is wearing a hat"),
                "Assertion recorded.");
        assertAnyOf(agent.handle("The boy under the table is holding a red ball"),
                "Assertion recorded.");
        assertAnyOf(agent.handle("The table is in the room"),
                "Assertion recorded.");
        assertAnyOf(agent.handle("The cat in the box is opposite the dog, and the dog is with the girl"),
                "Assertion recorded.");

        assertAnyOf(agent.handle("Where is the woman"),
                "entity:woman locatedIn entity:kitchen");
        assertAnyOf(agent.handle("Who is with the woman"),
                "entity:man");
        assertAnyOf(agent.handle("What is the man wearing"),
                "entity:hat");
        assertAnyOf(agent.handle("Is the man wearing a hat"),
                "Yes, the man is wearing a hat");

        assertAnyOf(agent.handle("Where is the boy"),
                "entity:boy locatedIn entity:room");
        assertAnyOf(agent.handle("Who is holding the ball"),
                "entity:boy");
        assertAnyOf(agent.handle("What color is the ball"),
                "red");
        assertAnyOf(agent.handle("How many people are in the room"),
                "1");

        assertAnyOf(agent.handle("Where is the dog"),
                "entity:dog locatedIn entity:box");
        assertAnyOf(agent.handle("Who is opposite the cat"),
                "entity:dog");
        assertAnyOf(agent.handle("Who is with the dog"),
                "entity:girl");

        assertAnyOf(agent.handle("Why is the woman with the man"),
                "Unknown.",
                "No candidates produced.");
        assertAnyOf(agent.handle("When is the boy holding the ball"),
                "Unknown.",
                "No candidates produced.");
        assertAnyOf(agent.handle("How is the man wearing a hat"),
                "Unknown.",
                "No candidates produced.");
    }

    private SahrAgent newAgent() {
        return new SahrAgent(
                new InMemoryKnowledgeBase(),
                HeadOntologyTestSupport.createOntology(),
                new SahrReasoner(List.of(
                        new AssertionInsertionHead(),
                        new RelationPropagationHead(),
                        new SubgoalExpansionHead(),
                        new ContainmentPropagationHead(),
                        new SurfaceContactPropagationHead(),
                        new OntologyReasoningHead(),
                        new GraphRetrievalHead(),
                        new RelationQueryHead(),
                        new AttributeQueryHead(),
                        new DependencyChainHead(),
                        new QueryAlignmentHead()
                )),
                new SimpleQueryParser()
        );
    }

    private void assertAnyOf(String actual, String... expected) {
        assertTrue(Set.of(expected).contains(actual),
                () -> "Unexpected answer: " + actual);
    }
}
