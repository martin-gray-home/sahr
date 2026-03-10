package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SahrAgentIngestionTest {
    @Test
    void ingestsStatementThenAnswersWhereQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new GraphRetrievalHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The person is in the house"));
        assertEquals("entity:person locatedIn entity:house", agent.handle("Where is the person?"));
    }

    @Test
    void appliesPropagationClosureAfterAssertion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new GraphRetrievalHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));

        assertEquals("entity:hat locatedIn entity:room", agent.handle("Where is the hat"));
    }
}