package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.alignment.OntologyBackedSemanticAlignmentCompiler;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticNode;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwlAlignmentPipelineTest {

    @Test
    void importsAlignsAndReports() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology(IRI.create("https://example.org/ont"));
        OWLAnnotationProperty rdfsLabel = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label"));

        IRI personIri = IRI.create("https://en-word.net/id/oewn-00007846-n");
        OWLClass person = manager.getOWLDataFactory().getOWLClass(personIri);
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLDeclarationAxiom(person));
        OWLAnnotation personLabel = manager.getOWLDataFactory().getOWLAnnotation(
                rdfsLabel,
                manager.getOWLDataFactory().getOWLLiteral("person")
        );
        manager.addAxiom(ontology, manager.getOWLDataFactory().getOWLAnnotationAssertionAxiom(person.getIRI(), personLabel));

        OwlAlignmentPipeline pipeline = new OwlAlignmentPipeline(
                new OwlSemanticImporter(),
                OntologyBackedSemanticAlignmentCompiler.loadDefault()
        );

        OwlAlignmentResult result = pipeline.run(ontology, "TestOntology");
        AlignmentOutput output = result.alignment();

        assertEquals(1, result.report().classCount());
        assertEquals(0, result.report().objectPropertyCount());

        Optional<SemanticNode> personNode = output.canonicalNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals(personIri.toString()))
                .findFirst();

        assertTrue(personNode.isPresent());
        assertEquals("person-like", personNode.get().familyId());
        assertEquals(AlignmentConfidence.STRONG, personNode.get().confidence());
    }
}
