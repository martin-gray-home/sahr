package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import com.sahr.support.HeadOntologyTestSupport;
import com.sahr.support.OwlOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SahrAgentWorkingMemoryTest {
    @Test
    void activeEntitiesPersistAcrossQueries() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        ontology.addSubclass("concept:man", "concept:person");
        ontology.addSubclass("concept:woman", "concept:person");

        SymbolId man = new SymbolId("entity:man");
        SymbolId woman = new SymbolId("entity:woman");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("concept:man")));
        graph.addEntity(new EntityNode(woman, "woman", Set.of("concept:woman")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("concept:hat")));
        graph.addAssertion(new RelationAssertion(woman, "wear", hat, 0.9));

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));

        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        String whoIsWearing = agent.handle("Who is wearing the hat");
        assertEquals(true, Set.of("entity:man", "entity:woman", "entity:man, entity:woman").contains(whoIsWearing));
    }
}
