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
import com.sahr.nlp.NoopTermMapper;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.support.HeadOntologyTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexSentenceQueryScenarioTest {
    @Test
    void handlesComplexSentencesAndMixedQueries() {
        SahrAgent agent = newAgent();

        assertAnyOf(agent.handle("The red dog is in the box, the box is in the room, "
                        + "the red dog is with the black cat under the table in the room, "
                        + "the woman is on the chair in the room, "
                        + "and the man wearing a hat is with the woman"),
                "Assertion recorded.");

        assertAnyOf(agent.handle("Where is the dog"),
                "entity:dog in entity:room",
                "entity:dog in entity:box");
        assertAnyOf(agent.handle("Where is the box"),
                "entity:box in entity:room");
        assertAnyOf(agent.handle("Where is the cat"),
                "entity:cat in entity:room",
                "entity:cat in entity:table");
        assertAnyOf(agent.handle("Where is the table"),
                "entity:table in entity:room");
        assertAnyOf(agent.handle("Where is the woman"),
                "entity:woman in entity:room",
                "entity:woman in entity:chair");
        assertAnyOf(agent.handle("Where is the man"),
                "entity:man in entity:room",
                "entity:man in entity:chair");
        assertAnyOf(agent.handle("Where is the hat"),
                "entity:hat in entity:room",
                "entity:hat in entity:chair");

        assertAnyOf(agent.handle("Who is with the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf(agent.handle("Who is the dog with"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf(agent.handle("What is with the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat");
        assertAnyOf(agent.handle("Who is with the cat"),
                "entity:dog",
                "entity:red_dog",
                "entity:red_dog, entity:dog",
                "entity:dog, entity:red_dog");
        assertAnyOf(agent.handle("Who is with the woman"),
                "entity:man");
        assertAnyOf(agent.handle("Who is with the man"),
                "entity:woman");
        assertAnyOf(agent.handle("Who is on the chair"),
                "entity:woman");
        assertAnyOf(agent.handle("What is under the table"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat",
                "entity:dog",
                "entity:red_dog",
                "entity:red_dog, entity:dog",
                "entity:dog, entity:red_dog");
        assertAnyOf(agent.handle("What is opposite the dog"),
                "entity:cat",
                "entity:black_cat",
                "entity:black_cat, entity:cat",
                "entity:cat, entity:black_cat",
                "No candidates produced.");

        assertAnyOf(agent.handle("What color is the dog"),
                "red");
        assertAnyOf(agent.handle("What color is the cat"),
                "black");

        assertAnyOf(agent.handle("Is the dog with the cat"),
                "Yes, the dog is with the cat",
                "Yes, the dog with the cat");
        assertAnyOf(agent.handle("Is the cat with the dog"),
                "Yes, the cat is with the dog",
                "Yes, the cat with the dog");
        assertAnyOf(agent.handle("Is the woman on the chair"),
                "Yes, the woman is on the chair",
                "Yes, the woman on the chair");
        assertAnyOf(agent.handle("Is the man with the woman"),
                "Yes, the man is with the woman",
                "Yes, the man with the woman");
        assertAnyOf(agent.handle("Is the dog in the room"),
                "Yes, the dog is in the room",
                "Yes, the dog in the room",
                "No candidates produced.");
        assertAnyOf(agent.handle("Is the dog in the box"),
                "Yes, the dog is in the box",
                "Yes, the dog in the box",
                "No candidates produced.");

        assertAnyOf(agent.handle("How many people are in the room"),
                "2");
        assertAnyOf(agent.handle("How many people are with the woman"),
                "1");
        assertAnyOf(agent.handle("How many people are with the dog"),
                "0");
        assertAnyOf(agent.handle("How many people are on the chair"),
                "1",
                "entity:woman");
    }

    private SahrAgent newAgent() {
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
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
                parser,
                statementParser,
                new NoopTermMapper()
        );
    }

    private void assertAnyOf(String actual, String... expected) {
        assertTrue(Set.of(expected).contains(actual),
                () -> "Unexpected answer: " + actual);
    }
}
