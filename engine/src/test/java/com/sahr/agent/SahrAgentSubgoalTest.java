package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.core.CandidateType;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.SubgoalExpansionHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentSubgoalTest {
    @Test
    void resolvesWhereQueryViaSubgoalQueue() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = new InMemoryOntologyService();

        SymbolId man = new SymbolId("entity:man");
        SymbolId hat = new SymbolId("entity:hat");
        graph.addEntity(new EntityNode(man, "man", Set.of("concept:man")));
        graph.addEntity(new EntityNode(hat, "hat", Set.of("concept:hat")));
        graph.addAssertion(new RelationAssertion(man, "https://sahr.ai/ontology/relations#wear", hat, 0.9));
        graph.addAssertion(new RelationAssertion(man, "locatedIn", new SymbolId("entity:room"), 0.9));

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new SubgoalExpansionHead(),
                new RelationPropagationHead(),
                new GraphRetrievalHead()
        ));

        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("entity:hat locatedIn entity:room", agent.handle("Where is the hat"));

        boolean sawSubgoal = agent.trace()
                .map(trace -> trace.entries().stream()
                        .flatMap(entry -> entry.candidates().stream())
                        .anyMatch(candidate -> candidate.type() == CandidateType.SUBGOAL))
                .orElse(false);
        assertTrue(sawSubgoal);
    }
}
