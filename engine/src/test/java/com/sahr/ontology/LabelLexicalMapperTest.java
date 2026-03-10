package com.sahr.ontology;

import com.sahr.nlp.TermMapper;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelLexicalMapperTest {
    @Test
    void mapsRdfsLabelToIri() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        IRI base = IRI.create("http://example.org/test#");
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));
        OWLClass doctor = manager.getOWLDataFactory().getOWLClass(IRI.create(base + "Doctor"));

        OWLAnnotation label = manager.getOWLDataFactory().getOWLAnnotation(
                manager.getOWLDataFactory().getRDFSLabel(),
                manager.getOWLDataFactory().getOWLLiteral("doctor")
        );
        OWLAnnotationAssertionAxiom axiom = manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(doctor.getIRI(), label);
        manager.addAxiom(ontology, axiom);

        TermMapper mapper = new LabelLexicalMapper(ontology);
        Optional<String> mapped = mapper.mapToken("doctor");

        assertTrue(mapped.isPresent());
        assertEquals(base + "Doctor", mapped.get());
    }

    @Test
    void mapsPropertyLabelToIri() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        IRI base = IRI.create("http://example.org/test#");
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));
        OWLObjectProperty wears = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(base + "wears"));

        OWLAnnotation label = manager.getOWLDataFactory().getOWLAnnotation(
                manager.getOWLDataFactory().getRDFSLabel(),
                manager.getOWLDataFactory().getOWLLiteral("wear")
        );
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLDeclarationAxiom(wears));
        OWLAnnotationAssertionAxiom axiom = manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(wears.getIRI(), label);
        manager.addAxiom(ontology, axiom);

        TermMapper mapper = new LabelLexicalMapper(ontology);
        Optional<String> mapped = mapper.mapPredicateToken("wear");

        assertTrue(mapped.isPresent());
        assertEquals(base + "wears", mapped.get());
    }
}