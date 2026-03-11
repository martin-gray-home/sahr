package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.ontology.LabelLexicalMapper;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import java.util.List;
import com.sahr.support.HeadOntologyTestSupport;
import com.sahr.support.OwlOntologyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SahrAgentLexicalMappingTest {
    @Test
    void mapsLexicalLabelToIriForQueriesAndPredicates() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        IRI base = IRI.create("http://example.org/test#");
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));
        OWLClass doctor = manager.getOWLDataFactory().getOWLClass(IRI.create(base + "Doctor"));
        OWLObjectProperty wears = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(base + "wears"));

        OWLAnnotation doctorLabel = manager.getOWLDataFactory().getOWLAnnotation(
                manager.getOWLDataFactory().getRDFSLabel(),
                manager.getOWLDataFactory().getOWLLiteral("doctor")
        );
        OWLAnnotationAssertionAxiom doctorAxiom = manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(doctor.getIRI(), doctorLabel);
        manager.addAxiom(ontology, doctorAxiom);

        OWLAnnotation wearLabel = manager.getOWLDataFactory().getOWLAnnotation(
                manager.getOWLDataFactory().getRDFSLabel(),
                manager.getOWLDataFactory().getOWLLiteral("wear")
        );
        OWLAnnotationAssertionAxiom wearAxiom = manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(wears.getIRI(), wearLabel);
        manager.addAxiom(ontology, wearAxiom);

        LabelLexicalMapper mapper = new LabelLexicalMapper(ontology);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontologyService = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));
        SahrAgent agent = new SahrAgent(graph, ontologyService, reasoner, new SimpleQueryParser(), mapper);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is a doctor"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:man in entity:room", agent.handle("Where is the doctor"));
    }

    @Test
    void dropsQueriesWithUnmappedPredicatesWhenMapperIsPresent() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        IRI base = IRI.create("http://example.org/test#");
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));
        OWLObjectProperty wears = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(base + "wears"));

        OWLAnnotation wearLabel = manager.getOWLDataFactory().getOWLAnnotation(
                manager.getOWLDataFactory().getRDFSLabel(),
                manager.getOWLDataFactory().getOWLLiteral("wear")
        );
        OWLAnnotationAssertionAxiom wearAxiom = manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(wears.getIRI(), wearLabel);
        manager.addAxiom(ontology, wearAxiom);

        LabelLexicalMapper mapper = new LabelLexicalMapper(ontology);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OntologyService ontologyService = HeadOntologyTestSupport.createOntology();
        SahrReasoner reasoner = new SahrReasoner(List.of(
                new OntologyDefinedHead(OwlOntologyTestSupport.buildHeadDefinitions())
        ));
        SahrAgent agent = new SahrAgent(graph, ontologyService, reasoner, new SimpleQueryParser(), mapper);

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("No candidates produced.", agent.handle("Who is with the man"));
    }
}
