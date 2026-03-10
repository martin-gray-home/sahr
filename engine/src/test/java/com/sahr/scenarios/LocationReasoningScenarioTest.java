package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.nlp.NoopTermMapper;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationReasoningScenarioTest {
    @Test
    void answersLocationQueriesAfterPropagation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new OntologyReasoningHead(),
                new GraphRetrievalHead(),
                new RelationQueryHead(),
                new QueryAlignmentHead()
        ));
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, parser, statementParser, new NoopTermMapper());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Assertion recorded.", agent.handle("A woman is with the man"));

        assertEquals("entity:man in entity:room", agent.handle("Where is the man"));
        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));
        assertEquals("entity:woman in entity:room", agent.handle("Where is the woman"));
    }
}
