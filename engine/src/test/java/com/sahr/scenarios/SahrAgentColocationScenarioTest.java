package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
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
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentColocationScenarioTest {
    @Test
    void answersLocationViaWithRelation() {
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
                new RelationQueryHead(),
                new DependencyChainHead(),
                new QueryAlignmentHead()
        ));
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, parser, statementParser, new NoopTermMapper());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The woman is with the man"));

        assertTrue(graph.getAllAssertions().stream().anyMatch(assertion ->
                "in".equals(assertion.predicate())
                        && "entity:man".equals(assertion.subject().value())
                        && "entity:room".equals(assertion.object().value())));
        assertTrue(graph.getAllAssertions().stream().anyMatch(assertion ->
                "with".equals(assertion.predicate())
                        && "entity:woman".equals(assertion.subject().value())
                        && "entity:man".equals(assertion.object().value())));

        String answer = agent.handle("Where is the woman");
        assertEquals("entity:woman in entity:room", answer);
    }
}
