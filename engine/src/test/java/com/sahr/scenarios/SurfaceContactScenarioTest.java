package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.SurfaceContactPropagationHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceContactScenarioTest {
    @Test
    void answersSurfaceContactLocationScenario() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        String surface = "https://sahr.ai/ontology/relations#surfaceContact";
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        ontology.addSubproperty(on, surface);
        ontology.addInverseProperty(on, under);

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new SurfaceContactPropagationHead(),
                new GraphRetrievalHead(),
                new QueryAlignmentHead()
        ));
        TermMapper mapper = new TermMapper() {
            @Override
            public Optional<String> mapToken(String token) {
                return Optional.empty();
            }

            @Override
            public Optional<String> mapPredicateToken(String token) {
                if ("on".equals(token)) {
                    return Optional.of(on);
                }
                if ("under".equals(token)) {
                    return Optional.of(under);
                }
                return Optional.empty();
            }
        };
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser(), mapper);

        assertEquals("Assertion recorded.", agent.handle("The hat is on the man"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("entity:hat locatedIn entity:room", agent.handle("Where is the hat"));
    }
}
