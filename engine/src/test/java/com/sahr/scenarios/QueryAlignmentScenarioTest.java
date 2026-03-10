package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryAlignmentScenarioTest {
    @Test
    void answersWhereUsingRangeAlignedPredicate() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        String inside = "https://sahr.ai/ontology/relations#inside";
        String place = "http://example.org/test#Place";
        ontology.addPropertyRange(inside, place);

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new QueryAlignmentHead()
        ));
        TermMapper mapper = new TermMapper() {
            @Override
            public Optional<String> mapToken(String token) {
                if ("location".equals(token)) {
                    return Optional.of(place);
                }
                return Optional.empty();
            }

            @Override
            public Optional<String> mapPredicateToken(String token) {
                return Optional.empty();
            }
        };
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, parser, statementParser, mapper);

        SymbolId catId = new SymbolId("entity:cat");
        graph.addEntity(new EntityNode(catId, "cat", Set.of("concept:cat")));
        graph.addAssertion(new RelationAssertion(
                catId,
                inside,
                new SymbolId("entity:box"),
                0.9
        ));

        assertEquals("entity:cat inside entity:box", agent.handle("Where is the cat"));
    }
}
