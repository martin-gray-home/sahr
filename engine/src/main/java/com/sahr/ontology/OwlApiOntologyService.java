package com.sahr.ontology;

import com.sahr.core.OntologyService;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.InferenceType;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OwlApiOntologyService implements OntologyService {
    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private final OWLOntologyManager manager;

    public OwlApiOntologyService(OWLOntology ontology) {
        this.ontology = ontology;
        this.manager = OWLManager.createOWLOntologyManager();
        OWLReasonerFactory factory = new ElkReasonerFactory();
        this.reasoner = factory.createReasoner(ontology);
        this.reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY, InferenceType.OBJECT_PROPERTY_HIERARCHY);
    }

    @Override
    public boolean isSubclassOf(String child, String parent) {
        if (!isIri(child) || !isIri(parent)) {
            return false;
        }
        OWLClass childClass = manager.getOWLDataFactory().getOWLClass(IRI.create(child));
        OWLClass parentClass = manager.getOWLDataFactory().getOWLClass(IRI.create(parent));
        return reasoner.getSuperClasses(childClass, false).containsEntity(parentClass);
    }

    @Override
    public boolean isSymmetricProperty(String property) {
        if (!isIri(property)) {
            return false;
        }
        OWLObjectProperty prop = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(property));
        return ontology.getAxioms().stream()
                .filter(axiom -> axiom.isOfType(org.semanticweb.owlapi.model.AxiomType.SYMMETRIC_OBJECT_PROPERTY))
                .anyMatch(axiom -> axiom.getObjectPropertiesInSignature().contains(prop));
    }

    @Override
    public boolean isTransitiveProperty(String property) {
        if (!isIri(property)) {
            return false;
        }
        OWLObjectProperty prop = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(property));
        return ontology.getAxioms().stream()
                .filter(axiom -> axiom.isOfType(org.semanticweb.owlapi.model.AxiomType.TRANSITIVE_OBJECT_PROPERTY))
                .anyMatch(axiom -> axiom.getObjectPropertiesInSignature().contains(prop));
    }

    @Override
    public Optional<String> getInverseProperty(String property) {
        if (!isIri(property)) {
            return Optional.empty();
        }
        OWLObjectProperty prop = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(property));
        return ontology.getAxioms().stream()
                .filter(axiom -> axiom.isOfType(org.semanticweb.owlapi.model.AxiomType.INVERSE_OBJECT_PROPERTIES))
                .filter(axiom -> axiom.getObjectPropertiesInSignature().contains(prop))
                .flatMap(axiom -> axiom.getObjectPropertiesInSignature().stream())
                .filter(found -> !found.equals(prop))
                .map(found -> found.getIRI().toString())
                .findFirst();
    }

    @Override
    public Set<String> getSuperclasses(String concept) {
        if (!isIri(concept)) {
            return Set.of();
        }
        OWLClass cls = manager.getOWLDataFactory().getOWLClass(IRI.create(concept));
        return reasoner.getSuperClasses(cls, false).getFlattened().stream()
                .map(found -> found.getIRI().toString())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getSubclasses(String concept) {
        if (!isIri(concept)) {
            return Set.of();
        }
        OWLClass cls = manager.getOWLDataFactory().getOWLClass(IRI.create(concept));
        return reasoner.getSubClasses(cls, false).getFlattened().stream()
                .map(found -> found.getIRI().toString())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getSubproperties(String property) {
        if (!isIri(property)) {
            return Set.of();
        }
        OWLObjectProperty prop = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(property));
        NodeSet<OWLObjectPropertyExpression> subs = reasoner.getSubObjectProperties(prop, false);
        return subs.getFlattened().stream()
                .filter(found -> !found.isAnonymous())
                .map(found -> found.asOWLObjectProperty())
                .filter(found -> !found.equals(prop))
                .map(found -> found.getIRI().toString())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getObjectPropertyRanges(String property) {
        if (!isIri(property)) {
            return Set.of();
        }
        OWLObjectProperty prop = manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(property));
        Stream<OWLObjectPropertyRangeAxiom> axioms = ontology.getObjectPropertyRangeAxioms(prop).stream();
        return axioms
                .flatMap(axiom -> axiom.getRange().classesInSignature())
                .map(cls -> cls.getIRI().toString())
                .collect(Collectors.toSet());
    }

    private boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }
}
