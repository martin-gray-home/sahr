package com.sahr.core;

import com.sahr.ontology.SemanticTypeCompatibilityService;
import com.sahr.support.HeadOntologyTestSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolicWorkingSetExecutionTest {
    @Test
    void builderCreatesReducedReadViewAroundCurrentContext() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");
        SymbolId room = new SymbolId("entity:room");
        SymbolId woman = new SymbolId("entity:woman");
        SymbolId box = new SymbolId("entity:box");

        graph.addAssertion(new RelationAssertion(man, "wear", hat, 0.9));
        graph.addAssertion(new RelationAssertion(hat, "in", room, 0.9));
        graph.addAssertion(new RelationAssertion(woman, "carry", box, 0.8));

        WorkingMemory memory = new WorkingMemory();
        memory.addActiveEntity(man);
        memory.recordAssertion(new RelationAssertion(man, "wear", hat, 0.9));

        HeadContext context = new HeadContext(
                QueryGoal.relation("entity:man", "wear", null, null),
                graph,
                HeadOntologyTestSupport.createPolicyOntology(),
                memory
        );

        SymbolicWorkingSet workingSet = new SymbolicWorkingSetBuilder().buildWorkingSet(context, graph);
        KnowledgeBase focused = workingSet.view();

        assertTrue(focused instanceof FocusedKnowledgeBase);
        assertTrue(workingSet.reduced());
        assertEquals(2, focused.getAllAssertions().size());
        assertTrue(focused.getAllAssertions().stream().anyMatch(assertion -> assertion.predicate().equals("wear")));
        assertTrue(focused.getAllAssertions().stream().noneMatch(assertion -> assertion.predicate().equals("carry")));
        assertTrue(workingSet.entities().stream().anyMatch(entity ->
                entity.entity().value().equals("entity:man")
                        && entity.reasons().stream().anyMatch(reason -> reason.contains("query.subject"))));
    }

    @Test
    void queryExecutorMergesFocusedAndFullBindingsWithoutDroppingTruth() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        var ontology = HeadOntologyTestSupport.createPolicyOntology();
        QueryExecutor executor = new QueryExecutor(new PredicateResolver(java.util.Map.of()));

        SymbolId man = new SymbolId("entity:man");
        SymbolId woman = new SymbolId("entity:woman");
        SymbolId hat = new SymbolId("entity:hat");
        SymbolId coat = new SymbolId("entity:coat");

        graph.addAssertion(new RelationAssertion(man, "wear", hat, 0.9));
        graph.addAssertion(new RelationAssertion(woman, "wear", coat, 0.8));

        Set<String> focusedAssertions = Set.of(FocusedKnowledgeBase.assertionKey(man, "wear", hat));
        KnowledgeBase focused = new FocusedKnowledgeBase(
                graph,
                focusedAssertions,
                Set.of(),
                Set.of(man, hat),
                true
        );

        QueryFrame frame = new QueryFrame(
                QueryOperator.COUNT,
                null,
                "wear",
                null,
                QueryFrame.TargetSlot.ANY,
                null,
                null,
                true
        );
        QueryResult result = executor.execute(
                frame,
                graph,
                focused,
                ontology,
                new SemanticTypeCompatibilityService(ontology)
        );

        assertEquals(2L, result.count());
        assertEquals(2, result.bindings().size());
    }

    @Test
    void ruleDerivationMergesFocusedAndFullRuleMatchesWithoutDroppingTruth() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        RuleDerivationService service = new RuleDerivationService();

        SymbolId hatOne = new SymbolId("entity:hat1");
        SymbolId hatTwo = new SymbolId("entity:hat2");
        SymbolId house = new SymbolId("entity:house");

        graph.addEntity(new EntityNode(hatOne, "hat1", Set.of("concept:hat")));
        graph.addEntity(new EntityNode(hatTwo, "hat2", Set.of("concept:hat")));
        graph.addEntity(new EntityNode(house, "house", Set.of("concept:house")));
        graph.addAssertion(new RelationAssertion(hatOne, "rdf:type", new SymbolId("concept:hat"), 0.9));
        graph.addAssertion(new RelationAssertion(hatOne, "in", house, 0.9));
        graph.addAssertion(new RelationAssertion(hatTwo, "rdf:type", new SymbolId("concept:hat"), 0.9));
        graph.addAssertion(new RelationAssertion(hatTwo, "in", house, 0.9));

        RuleFrame rule = new RuleFrame(
                "x",
                List.of(
                        new RuleAtom(RuleTerm.variable("x"), "rdf:type", RuleTerm.constant("concept:hat")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:house"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "hasAttribute", RuleTerm.constant("concept:green")),
                0.85
        );
        graph.addRuleFrame(rule);

        Set<String> focusedAssertions = new LinkedHashSet<>();
        focusedAssertions.add(FocusedKnowledgeBase.assertionKey(hatOne, "rdf:type", new SymbolId("concept:hat")));
        focusedAssertions.add(FocusedKnowledgeBase.assertionKey(hatOne, "in", house));
        KnowledgeBase focused = new FocusedKnowledgeBase(
                graph,
                focusedAssertions,
                Set.of(rule),
                Set.of(hatOne, house),
                true
        );

        List<RuleDerivation> derivations = service.derive(graph, focused);

        assertEquals(2, derivations.size());
        assertEquals("entity:hat1", derivations.get(0).assertion().subject().value());
        assertTrue(derivations.stream().anyMatch(derivation ->
                derivation.assertion().subject().value().equals("entity:hat2")
                        && derivation.assertion().predicate().equals("hasAttribute")));
    }
}
