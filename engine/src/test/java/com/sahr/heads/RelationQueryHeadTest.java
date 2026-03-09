package com.sahr.heads;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RelationQueryHeadTest {
    private final RelationQueryHead head = new RelationQueryHead();

    @Test
    void answersForwardRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = new InMemoryOntologyService();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("hat")));
        graph.addAssertion(new RelationAssertion(man, "wear", hat, 0.9));

        QueryGoal query = QueryGoal.relation("entity:man", "wear", null, null);
        HeadContext context = new HeadContext(query, graph, ontology);

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:hat"), candidates.get(0).payload());
    }

    @Test
    void answersInverseRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = new InMemoryOntologyService();
        SymbolId man = new SymbolId("entity:man");
        SymbolId woman = new SymbolId("entity:woman");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(woman, "woman", Set.of("person")));
        graph.addAssertion(new RelationAssertion(woman, "with", man, 0.9));

        QueryGoal query = QueryGoal.relation("entity:man", "with", null, null);
        HeadContext context = new HeadContext(query, graph, ontology);

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:woman"), candidates.get(0).payload());
    }

    @Test
    void answersObjectBoundRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("hat")));
        graph.addAssertion(new RelationAssertion(man, "https://sahr.ai/ontology/relations#wear", hat, 0.9));
        ontology.addSubproperty("https://sahr.ai/ontology/relations#wear", "https://sahr.ai/ontology/relations#on");
        ontology.addInverseProperty("https://sahr.ai/ontology/relations#on", "https://sahr.ai/ontology/relations#under");

        QueryGoal query = QueryGoal.relation(null, "https://sahr.ai/ontology/relations#wear", "entity:hat", null);
        HeadContext context = new HeadContext(query, graph, ontology);

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:man"), candidates.get(0).payload());
    }

    @Test
    void answersSubpropertyAndInverseRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("hat")));
        String wear = "https://sahr.ai/ontology/relations#wear";
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        graph.addAssertion(new RelationAssertion(man, wear, hat, 0.9));

        ontology.addSubproperty(wear, on);
        ontology.addInverseProperty(on, under);

        QueryGoal query = QueryGoal.relation("entity:man", on, null, null);
        HeadContext context = new HeadContext(query, graph, ontology);

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:hat"), candidates.get(0).payload());

        QueryGoal inverseQuery = QueryGoal.relation("entity:hat", under, null, null);
        HeadContext inverseContext = new HeadContext(inverseQuery, graph, ontology);

        List<ReasoningCandidate> inverseCandidates = head.evaluate(inverseContext);
        assertFalse(inverseCandidates.isEmpty());
        assertEquals(new SymbolId("entity:man"), inverseCandidates.get(0).payload());
    }
}
