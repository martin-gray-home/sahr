package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationInferenceScenarioTest {
    @Test
    void answersRelationQueriesEndToEnd() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:man", agent.handle("Who is wearing a hat"));
    }

    @Test
    void answersDativeAndPassiveQueriesEndToEnd() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man gave the book to the boy"));
        assertEquals("entity:book, entity:boy", agent.handle("Who did the man give the book to"));
        assertEquals("entity:book, entity:boy", agent.handle("What did the man give the boy"));

        assertEquals("Assertion recorded.", agent.handle("The hat was worn by the man"));
        assertEquals("Yes, the man was worn by the hat", agent.handle("Was the hat worn by the man"));
    }
}
