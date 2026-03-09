package com.sahr.ontology;

import com.sahr.nlp.TermMapper;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLEntity;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class LabelLexicalMapper implements TermMapper {
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String ONTOLEX_WRITTEN_REP = "http://www.w3.org/ns/lemon/ontolex#writtenRep";

    private final Map<String, String> labelToEntityIri = new ConcurrentHashMap<>();
    private final Map<String, String> labelToPropertyIri = new ConcurrentHashMap<>();

    public LabelLexicalMapper(OWLOntology ontology) {
        indexAnnotations(ontology);
        indexDataProperties(ontology);
        indexObjectPropertyLabels(ontology);
    }

    @Override
    public Optional<String> mapToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(labelToEntityIri.get(normalize(token)));
    }

    @Override
    public Optional<String> mapPredicateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(labelToPropertyIri.get(normalize(token)));
    }

    private void indexAnnotations(OWLOntology ontology) {
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            IRI propertyIri = axiom.getProperty().getIRI();
            if (!isLabelProperty(propertyIri)) {
                continue;
            }
            OWLAnnotationValue value = axiom.getValue();
            if (value instanceof OWLLiteral) {
                String label = ((OWLLiteral) value).getLiteral();
                String key = normalize(label);
                axiom.getSubject().asIRI().ifPresent(subjectIri -> {
                    labelToEntityIri.putIfAbsent(key, subjectIri.toString());
                    if (ontology.containsObjectPropertyInSignature(subjectIri)) {
                        labelToPropertyIri.putIfAbsent(key, subjectIri.toString());
                    }
                });
            }
        }
    }

    private void indexDataProperties(OWLOntology ontology) {
        for (OWLDataPropertyAssertionAxiom axiom : ontology.getAxioms(AxiomType.DATA_PROPERTY_ASSERTION)) {
            IRI propertyIri = axiom.getProperty().asOWLDataProperty().getIRI();
            if (!ONTOLEX_WRITTEN_REP.equals(propertyIri.toString())) {
                continue;
            }
            if (axiom.getObject() instanceof OWLLiteral) {
                OWLLiteral literal = (OWLLiteral) axiom.getObject();
                String key = normalize(literal.getLiteral());
                if (axiom.getSubject().isNamed()) {
                    IRI subjectIri = axiom.getSubject().asOWLNamedIndividual().getIRI();
                    labelToEntityIri.putIfAbsent(key, subjectIri.toString());
                }
            }
        }
    }

    private void indexObjectPropertyLabels(OWLOntology ontology) {
        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            for (OWLAnnotationAssertionAxiom axiom : ontology.getAnnotationAssertionAxioms(property.getIRI())) {
                IRI propertyIri = axiom.getProperty().getIRI();
                if (!isLabelProperty(propertyIri)) {
                    continue;
                }
                if (axiom.getValue() instanceof OWLLiteral) {
                    String label = ((OWLLiteral) axiom.getValue()).getLiteral();
                    labelToPropertyIri.putIfAbsent(normalize(label), property.getIRI().toString());
                }
            }
        }
    }

    private boolean isLabelProperty(IRI iri) {
        String value = iri.toString();
        return RDFS_LABEL.equals(value) || SKOS_PREF_LABEL.equals(value);
    }

    private String normalize(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.replaceAll("\\s+", "_");
    }
}
