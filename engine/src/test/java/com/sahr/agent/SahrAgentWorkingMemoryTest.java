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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentWorkingMemoryTest {
    @Test
    void activeEntitiesPersistAcrossQueries() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        baseOntology.addSubclass("concept:man", "concept:person");
        baseOntology.addSubclass("concept:woman", "concept:person");
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

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

    @Test
    void explainShowsWorkingSetWithInclusionReasons() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        baseOntology.addSubclass("concept:man", "concept:person");
        baseOntology.addSubclass("concept:hat", "concept:thing");
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");
        graph.addEntity(new EntityNode(man, "man", Set.of("concept:man")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("concept:hat")));

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));

        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());
        assertEquals("Assertion recorded.", agent.handle("The man is wearing the hat"));
        agent.handle("Who is wearing the hat");

        var entry = agent.lastTraceEntry().orElseThrow();
        assertTrue(entry.workingSet() != null);
        assertTrue(entry.workingSet().entities().stream().anyMatch(included ->
                included.entity().value().equals("entity:hat")
                        && included.reasons().stream().anyMatch(reason -> reason.contains("query.object"))));
        assertTrue(entry.workingSet().assertions().stream().anyMatch(included ->
                included.reasons().stream().anyMatch(reason -> reason.contains("working_memory.recent_assertion"))));

        CommandProcessor processor = new CommandProcessor(agent);
        String explain = processor.handle(":explain --depth 3 --verbose").output();
        assertTrue(explain.contains("Working set details"));
        assertTrue(explain.contains("working_memory.recent_assertion"));
    }
}
