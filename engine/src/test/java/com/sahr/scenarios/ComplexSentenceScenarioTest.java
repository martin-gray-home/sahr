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
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexSentenceScenarioTest {
    @Test
    void answersQuestionsFromCompoundSentence() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new SubgoalExpansionHead(),
                new ContainmentPropagationHead(),
                new SurfaceContactPropagationHead(),
                new OntologyReasoningHead(),
                new GraphRetrievalHead(),
                new AttributeQueryHead(),
                new RelationQueryHead(),
                new DependencyChainHead(),
                new QueryAlignmentHead()
        ));
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, parser, statementParser, new NoopTermMapper());

        assertEquals("Assertion recorded.", agent.handle("The man and the boy in the room are with the red dog."));

        assertEquals("entity:man locatedIn entity:room", agent.handle("Where is the man"));
        assertEquals("entity:boy locatedIn entity:room", agent.handle("Where is the boy"));
        assertEquals("entity:dog locatedIn entity:room", agent.handle("Where is the dog"));

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
}
