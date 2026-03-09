package com.sahr.scenarios;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.ContainmentPropagationHead;
import com.sahr.heads.DependencyChainHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SurfaceContactPropagationHead;
import com.sahr.heads.SubgoalExpansionHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.InMemoryOntologyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReasoningScenarioSuiteTest {
    @Test
    void infersHatLocationViaPartOfChain() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        String on = "https://sahr.ai/ontology/relations#on";
        String surface = "https://sahr.ai/ontology/relations#surfaceContact";
        ontology.addSubproperty(on, surface);

        SahrAgent agent = newAgent(graph, ontology, new TermMapper() {
            @Override
            public Optional<String> mapToken(String token) {
                return Optional.empty();
            }

            @Override
            public Optional<String> mapPredicateToken(String token) {
                if ("on".equals(token)) {
                    return Optional.of(on);
                }
                return Optional.empty();
            }
        });

        assertEquals("Assertion recorded.", agent.handle("The hat is on the head"));
        assertEquals("Assertion recorded.", agent.handle("The head is part of the man"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("entity:hat locatedIn entity:room", agent.handle("Where is the hat"));
    }

    @Test
    void infersLocationForCarriedObject() {
        SahrAgent agent = newAgent(new InMemoryKnowledgeBase(), new InMemoryOntologyService(), null);

        assertEquals("Assertion recorded.", agent.handle("The woman is in the garden"));
        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));
        assertEquals("entity:bag locatedIn entity:garden", agent.handle("Where is the bag"));
    }

    @Test
    void infersNestedContainmentChain() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        SahrAgent agent = newAgent(graph, ontology, null);

        assertEquals("Assertion recorded.", agent.handle("The book is in the bag"));
        assertEquals("Assertion recorded.", agent.handle("The bag is in the car"));
        assertEquals("Assertion recorded.", agent.handle("The car is in the garage"));
        assertEquals("entity:book locatedIn entity:garage", agent.handle("Where is the book"));
    }

    @Test
    void answersMultiHopPowerChain() {
        SahrAgent agent = newAgent(new InMemoryKnowledgeBase(), new InMemoryOntologyService(), null);

        assertEquals("Assertion recorded.", agent.handle("The transmitter is powered by the power bus"));
        assertEquals("Assertion recorded.", agent.handle("The power bus is powered by the battery"));
        assertEquals("Assertion recorded.", agent.handle("The battery is charged by the solar array"));
        assertEquals("entity:solar_array", agent.handle("What powers the transmitter"));
    }

    @Test
    void answersInverseRelationQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        String on = "https://sahr.ai/ontology/relations#on";
        String under = "https://sahr.ai/ontology/relations#under";
        ontology.addInverseProperty(on, under);

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

        SahrAgent agent = newAgent(graph, ontology, mapper);

        assertEquals("Assertion recorded.", agent.handle("The cat is on the table"));
        assertEquals("entity:table", agent.handle("What is under the cat"));
    }

    @Test
    void infersLocationForWithRelation() {
        SahrAgent agent = newAgent(new InMemoryKnowledgeBase(), new InMemoryOntologyService(), null);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The woman is with the man"));
        assertEquals("entity:woman locatedIn entity:room", agent.handle("Where is the woman"));
    }

    @Test
    void answersInstrumentPowerChain() {
        SahrAgent agent = newAgent(new InMemoryKnowledgeBase(), new InMemoryOntologyService(), null);

        assertEquals("Assertion recorded.", agent.handle("Navcam is an instrument"));
        assertEquals("Assertion recorded.", agent.handle("Navcam observes the comet"));
        assertEquals("Assertion recorded.", agent.handle("Navcam is powered by the power bus"));
        assertEquals("Assertion recorded.", agent.handle("The power bus is powered by the battery"));
        assertEquals("entity:battery", agent.handle("What powers navcam"));
    }

    @Test
    void infersLocationForHeldObject() {
        SahrAgent agent = newAgent(new InMemoryKnowledgeBase(), new InMemoryOntologyService(), null);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is holding a key"));
        assertEquals("Assertion recorded.", agent.handle("The key opens the door"));
        assertEquals("entity:key locatedIn entity:room", agent.handle("Where is the key"));
    }

    private SahrAgent newAgent(InMemoryKnowledgeBase graph, InMemoryOntologyService ontology, TermMapper mapper) {
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new AssertionInsertionHead(),
                new RelationPropagationHead(),
                new SubgoalExpansionHead(),
                new ContainmentPropagationHead(),
                new SurfaceContactPropagationHead(),
                new OntologyReasoningHead(),
                new GraphRetrievalHead(),
                new RelationQueryHead(),
                new DependencyChainHead(),
                new QueryAlignmentHead()
        ));
        if (mapper == null) {
            return new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser());
        }
        return new SahrAgent(graph, ontology, reasoner, new SimpleQueryParser(), mapper);
    }
}
