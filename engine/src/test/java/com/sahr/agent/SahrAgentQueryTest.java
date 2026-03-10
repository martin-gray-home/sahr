package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.EntityNode;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SahrAgentQueryTest {
    @Test
    void answersWhoIsWithQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("entity:woman", agent.handle("Who is with the man"));
    }

    @Test
    void answersWhoIsWearingQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:man", agent.handle("Who is wearing a hat"));
    }

    @Test
    void answersYesNoWearQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        String wear = "https://sahr.ai/ontology/relations#wear";
        String wornBy = "https://sahr.ai/ontology/relations#wornBy";
        String on = "https://sahr.ai/ontology/relations#on";
        ontology.addInverseProperty(wear, wornBy);
        ontology.addSubproperty(wornBy, on);
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new OntologyReasoningHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser(), new com.sahr.nlp.TermMapper() {
            @Override
            public java.util.Optional<String> mapToken(String token) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> mapPredicateToken(String token) {
                if ("wear".equals(token)) {
                    return java.util.Optional.of(wear);
                }
                if ("on".equals(token)) {
                    return java.util.Optional.of(on);
                }
                return java.util.Optional.empty();
            }
        });

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the man is wearing a hat", agent.handle("Is the man wearing a hat"));
    }

    @Test
    void answersYesNoOnQueryFromWear() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        String wear = "https://sahr.ai/ontology/relations#wear";
        String wornBy = "https://sahr.ai/ontology/relations#wornBy";
        String on = "https://sahr.ai/ontology/relations#on";
        ontology.addInverseProperty(wear, wornBy);
        ontology.addSubproperty(wornBy, on);
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new OntologyReasoningHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser(), new com.sahr.nlp.TermMapper() {
            @Override
            public java.util.Optional<String> mapToken(String token) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> mapPredicateToken(String token) {
                if ("wear".equals(token)) {
                    return java.util.Optional.of(wear);
                }
                if ("on".equals(token)) {
                    return java.util.Optional.of(on);
                }
                return java.util.Optional.empty();
            }
        });

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the hat is on the man", agent.handle("Is the hat on the man"));
    }

    @Test
    void answersUnknownForYesNoWithoutEvidence() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Unknown.", agent.handle("Is the woman wearing a hat"));
    }

    @Test
    void answersWhoElseQueryUsingHistory() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontology = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationQueryHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is with the mother"));
        assertEquals("Assertion recorded.", agent.handle("The boy is with the mother"));

        assertEquals("entity:man, entity:boy", agent.handle("Who is with the mother"));
        assertEquals("No candidates produced.", agent.handle("Who else is with the mother"));
    }

    @Test
    void answersWhereAfterOntologyAssertion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = HeadOntologyTestSupport.createOntology();
        String colocation = "https://sahr.ai/ontology/relations#colocation";
        String with = "https://sahr.ai/ontology/relations#with";
        ontology.addSubproperty(with, colocation);
        ontology.addSymmetricProperty("https://sahr.ai/ontology/relations#with");
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new OntologyReasoningHead(),
                new GraphRetrievalHead()
        ));
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                with,
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addEntity(new EntityNode(
                new SymbolId("entity:woman"),
                "woman",
                java.util.Set.of("concept:woman")
        ));

        assertEquals("entity:woman locatedIn entity:room", agent.handle("Where is the woman"));
    }
}
