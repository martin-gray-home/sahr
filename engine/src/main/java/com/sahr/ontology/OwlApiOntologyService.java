package com.sahr.ontology;

import com.sahr.core.OntologyService;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.InferenceType;

import java.util.HashSet;
import java.util.Locale;
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

    @Override
    public Set<String> getObjectPropertiesByLabel(String label) {
        if (label == null || label.isBlank()) {
            return Set.of();
        }
        String normalized = normalizeLabel(label);
        Set<String> matches = new HashSet<>();
        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            Set<String> labels = labelsForIri(property.getIRI());
            for (String candidate : labels) {
                if (normalizeLabel(candidate).equals(normalized)) {
                    matches.add(property.getIRI().toString());
                    break;
                }
            }
        }
        return matches;
    }

    @Override
    public Set<String> getEntityIrisByLabel(String label) {
        if (label == null || label.isBlank()) {
            return Set.of();
        }
        String normalized = normalizeLabel(label);
        Set<String> matches = new HashSet<>();
        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (matchesLabel(cls.getIRI(), normalized)) {
                matches.add(cls.getIRI().toString());
            }
        }
        for (OWLNamedIndividual individual : ontology.getIndividualsInSignature()) {
            if (matchesLabel(individual.getIRI(), normalized)) {
                matches.add(individual.getIRI().toString());
            }
        }
        return matches;
    }

    @Override
    public Set<String> getLabels(String iri) {
        if (!isIri(iri)) {
            return Set.of();
        }
        return labelsForIri(IRI.create(iri));
    }

    @Override
    public Optional<String> getAnnotationValue(String iri, String annotationIri) {
        if (!isIri(iri) || !isIri(annotationIri)) {
            return Optional.empty();
        }
        IRI subject = IRI.create(iri);
        IRI annotation = IRI.create(annotationIri);
        OWLAnnotationProperty property = manager.getOWLDataFactory().getOWLAnnotationProperty(annotation);
        return ontology.getAnnotationAssertionAxioms(subject).stream()
                .filter(ax -> ax.getProperty().equals(property))
                .map(OWLAnnotationAssertionAxiom::getValue)
                .filter(value -> value.isLiteral())
                .map(value -> value.asLiteral().orElse(null))
                .filter(literal -> literal != null)
                .map(literal -> literal.getLiteral())
                .findFirst();
    }

    private boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private boolean matchesLabel(IRI iri, String normalized) {
        for (String label : labelsForIri(iri)) {
            if (normalizeLabel(label).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> labelsForIri(IRI iri) {
        Set<String> labels = new HashSet<>();
        OWLAnnotationProperty rdfsLabel = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label"));
        OWLAnnotationProperty skosPrefLabel = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));
        OWLAnnotationProperty skosAltLabel = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel"));
        OWLAnnotationProperty ontolexWrittenRep = manager.getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/ns/lemon/ontolex#writtenRep"));
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAnnotationAssertionAxioms(iri)) {
            OWLAnnotationProperty property = axiom.getProperty();
            if (!property.equals(rdfsLabel)
                    && !property.equals(skosPrefLabel)
                    && !property.equals(skosAltLabel)
                    && !property.equals(ontolexWrittenRep)) {
                continue;
            }
            axiom.getValue().asLiteral().ifPresent(literal -> labels.add(literal.getLiteral()));
        }
        return labels;
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace('_', ' ');
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }
}
