package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.ContainmentPropagationHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainmentScenarioTest {
    @Test
    void answersContainmentLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        String containment = "https://sahr.ai/ontology/relations#containment";
        String inside = "https://sahr.ai/ontology/relations#inside";
        ontology.addSubproperty(inside, containment);
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new ContainmentPropagationHead(),
                new GraphRetrievalHead(),
                new QueryAlignmentHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The apple is inside the basket"));
        assertEquals("Assertion recorded.", agent.handle("The basket is in the kitchen"));
        assertEquals("entity:apple locatedIn entity:kitchen", agent.handle("Where is the apple"));
    }
}
