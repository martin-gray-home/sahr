package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentInput;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticNodeType;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwlSemanticImporterTest {

    @Test
    void importsClassesAndPropertiesWithLabels() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology(IRI.create("https://example.org/ont"));
        OWLAnnotationProperty rdfsLabel = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label"));

        OWLClass person = manager.getOWLDataFactory().getOWLClass(IRI.create("https://example.org/Person"));
        OWLAnnotation personLabel = manager.getOWLDataFactory().getOWLAnnotation(
                rdfsLabel,
                manager.getOWLDataFactory().getOWLLiteral("Person")
        );
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLDeclarationAxiom(person));
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(person.getIRI(), personLabel));

        OWLClass thing = manager.getOWLDataFactory().getOWLClass(IRI.create("https://example.org/Thing"));
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLDeclarationAxiom(thing));

        OWLObjectProperty locatedIn = manager.getOWLDataFactory()
                .getOWLObjectProperty(IRI.create("https://example.org/locatedIn"));
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLDeclarationAxiom(locatedIn));
        OWLAnnotation locatedInLabel = manager.getOWLDataFactory().getOWLAnnotation(
                rdfsLabel,
                manager.getOWLDataFactory().getOWLLiteral("located in")
        );
        manager.addAxiom(ontology, manager.getOWLDataFactory()
                .getOWLAnnotationAssertionAxiom(locatedIn.getIRI(), locatedInLabel));

        OwlSemanticImporter importer = new OwlSemanticImporter();
        OwlImportResult result = importer.importOntology(ontology, "TestOntology");

        AlignmentInput input = result.input();
        assertEquals(2, result.report().classCount());
        assertEquals(1, result.report().objectPropertyCount());
        assertEquals(1, result.report().classesMissingLabels());
        assertEquals(0, result.report().objectPropertiesMissingLabels());

        Optional<SemanticNode> personNode = input.importedNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals(person.getIRI().toString()))
                .findFirst();
        assertTrue(personNode.isPresent());
        assertEquals("Person", personNode.get().label());
        assertEquals(SemanticNodeType.CONCEPT, personNode.get().type());

        Optional<SemanticNode> propertyNode = input.importedNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals(locatedIn.getIRI().toString()))
                .findFirst();
        assertTrue(propertyNode.isPresent());
        assertEquals("located in", propertyNode.get().label());
        assertEquals(SemanticNodeType.RELATION, propertyNode.get().type());
    }
}
