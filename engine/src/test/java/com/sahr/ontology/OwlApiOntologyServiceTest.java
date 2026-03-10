package com.sahr.ontology;

import com.sahr.core.OntologyService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwlApiOntologyServiceTest {
    @Test
    void ignoresNonIriValues() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));

        OntologyService service = new OwlApiOntologyService(ontology);

        assertFalse(service.isSubclassOf("wife", "person"));
        assertTrue(service.getSuperclasses("wife").isEmpty());
    }

    @Test
    void handlesIriSubclassChecks() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        IRI base = IRI.create("http://example.org/test#");
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.org/test"));
        OWLClass child = manager.getOWLDataFactory().getOWLClass(IRI.create(base + "Child"));
        OWLClass parent = manager.getOWLDataFactory().getOWLClass(IRI.create(base + "Parent"));
        OWLAxiom axiom = manager.getOWLDataFactory().getOWLSubClassOfAxiom(child, parent);
        manager.addAxiom(ontology, axiom);

        OntologyService service = new OwlApiOntologyService(ontology);

        assertTrue(service.isSubclassOf(base + "Child", base + "Parent"));
        Set<String> supers = service.getSuperclasses(base + "Child");
        assertTrue(supers.contains(base + "Parent"));
    }
}