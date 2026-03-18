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
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationQueryHeadTest {
    private final RelationQueryHead head = new RelationQueryHead();

    @Test
    void answersForwardRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
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
        OntologyService ontology = HeadOntologyTestSupport.createPolicyOntology();
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
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("hat")));
        graph.addAssertion(new RelationAssertion(man, "https://sahr.ai/ontology/relations#wear", hat, 0.9));
        baseOntology.addSubproperty("https://sahr.ai/ontology/relations#wear", "https://sahr.ai/ontology/relations#on");
        baseOntology.addInverseProperty("https://sahr.ai/ontology/relations#on", "https://sahr.ai/ontology/relations#under");
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

        QueryGoal query = QueryGoal.relation(null, "https://sahr.ai/ontology/relations#wear", "entity:hat", null);
        HeadContext context = new HeadContext(query, graph, ontology);

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:man"), candidates.get(0).payload());
    }

    @Test
    void answersSubpropertyAndInverseRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");

        graph.addEntity(new EntityNode(man, "man", Set.of("person")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("hat")));
        String wear = "https://sahr.ai/ontology/relations#wear";
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        graph.addAssertion(new RelationAssertion(man, wear, hat, 0.9));

        baseOntology.addSubproperty(wear, on);
        baseOntology.addInverseProperty(on, under);
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

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

    @Test
    void countsGenericThingsFromInverseRelation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService baseOntology = HeadOntologyTestSupport.createOntology();
        String in = "https://sahr.ai/ontology/relations#in";
        String contains = "https://sahr.ai/ontology/relations#contains";
        baseOntology.addInverseProperty(in, contains);
        OntologyService ontology = HeadOntologyTestSupport.wrapWithPolicy(baseOntology);

        SymbolId box = new SymbolId("entity:box");
        SymbolId ball = new SymbolId("entity:ball");
        graph.addEntity(new EntityNode(box, "box", Set.of("box")));
        graph.addEntity(new EntityNode(ball, "ball", Set.of("ball")));
        graph.addAssertion(new RelationAssertion(box, contains, ball, 0.9));

        QueryGoal query = QueryGoal.relation(null, in, "entity:box", null);
        HeadContext context = new HeadContext(query, graph, ontology);
        List<ReasoningCandidate> candidates = head.evaluate(context);
        assertFalse(candidates.isEmpty());
        assertEquals(new SymbolId("entity:ball"), candidates.get(0).payload());

        QueryGoal countQuery = QueryGoal.count(null, in, "entity:box", "things", null);
        HeadContext countContext = new HeadContext(countQuery, graph, ontology);
        List<ReasoningCandidate> countCandidates = head.evaluate(countContext);
        assertFalse(countCandidates.isEmpty());
        Object payload = countCandidates.get(0).payload();
        assertTrue(payload instanceof com.sahr.core.QueryResult);
        com.sahr.core.QueryResult result = (com.sahr.core.QueryResult) payload;
        assertEquals(1L, result.count());
    }
}
