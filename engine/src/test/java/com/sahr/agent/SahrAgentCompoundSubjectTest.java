package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentCompoundSubjectTest {
    @Test
    void appliesAssertionsForCompoundSubjects() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrReasoner reasoner = new SahrReasoner(List.of(new AssertionInsertionHead()));
        SahrAgent agent = new SahrAgent(graph, new InMemoryOntologyService(), reasoner, new SimpleQueryParser());

        agent.handle("The man and the boy sat at the table");

        List<RelationAssertion> assertions = graph.findByPredicate("at");
        boolean hasMan = assertions.stream().anyMatch(assertion ->
                assertion.subject().value().equals("entity:man") && assertion.object().value().equals("entity:table"));
        boolean hasBoy = assertions.stream().anyMatch(assertion ->
                assertion.subject().value().equals("entity:boy") && assertion.object().value().equals("entity:table"));

        assertTrue(hasMan);
        assertTrue(hasBoy);
    }
}
