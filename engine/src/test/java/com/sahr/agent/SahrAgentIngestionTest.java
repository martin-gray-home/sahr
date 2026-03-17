package com.sahr.agent;

import com.sahr.core.AssertionLayer;
import com.sahr.core.AssertionRecord;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.support.OwlOntologyTestSupport;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SahrAgentIngestionTest {
    @Test
    void ingestsStatementThenAnswersWhereQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The person is in the house"));
        assertEquals("entity:person in entity:house", agent.handle("Where is the person?"));
    }

    @Test
    void appliesPropagationClosureAfterAssertion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));

        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));
    }

    @Test
    void ingestsLayeredAssertionRecordsForAdjectivalCopula() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The hat is green"));

        AssertionRecord canonical = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.CANONICAL)
                .filter(record -> record.subject().value().endsWith("hat"))
                .filter(record -> record.object().value().endsWith("green"))
                .findFirst()
                .orElse(null);
        AssertionRecord helper = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.DERIVED_HELPER)
                .filter(record -> record.subject().value().endsWith("hat"))
                .filter(record -> record.object().value().endsWith("green"))
                .findFirst()
                .orElse(null);

        assertNotNull(canonical);
        assertNotNull(helper);
        assertEquals(canonical.id(), helper.provenance().normalizedFromId());
    }
}
