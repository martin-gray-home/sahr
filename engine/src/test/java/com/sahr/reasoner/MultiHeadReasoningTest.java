package com.sahr.reasoner;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SurfaceContactPropagationHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiHeadReasoningTest {
    @Test
    void infersLocationViaWearThenAnswersWhere() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new GraphRetrievalHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:hat in entity:room", agent.handle("Where is the hat"));
    }

    @Test
    void infersLocationViaWithThenAnswersWhere() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new GraphRetrievalHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The woman is with the man"));
        assertEquals("entity:woman in entity:room", agent.handle("Where is the woman"));
    }

    @Test
    void answersYesNoAfterPropagation() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        String surface = "https://sahr.ai/ontology/relations#surfaceContact";
        ontology.addSubproperty(on, surface);
        ontology.addInverseProperty(on, under);

        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new OntologyReasoningHead(),
                new SurfaceContactPropagationHead(),
                new RelationQueryHead()
        ));
        TermMapper mapper = new TermMapper() {
            @Override
            public Optional<String> mapToken(String token) {
                return Optional.empty();
            }

            @Override
            public Optional<String> mapPredicateToken(String token) {
                if ("under".equals(token)) {
                    return Optional.of(under);
                }
                return Optional.empty();
            }
        };
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser(), mapper);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:hat"),
                under,
                new SymbolId("entity:man"),
                0.9
        ));

        assertEquals("Yes, the hat is under the man", agent.handle("Is the hat under the man"));
    }
}
