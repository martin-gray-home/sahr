package com.sahr.ontology;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.AxiomType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class OntologyAnnotationValidator {
    private static final String SAHR_NAMESPACE = "https://sahr.ai/ontology/";
    private static final Logger logger = Logger.getLogger(OntologyAnnotationValidator.class.getName());

    private final OWLOntology ontology;

    public OntologyAnnotationValidator(OWLOntology ontology) {
        this.ontology = ontology;
    }

    public void validate() {
        validateNumericAnnotation(SahrAnnotationVocabulary.DYNAMIC_WEIGHT);
        validateNumericAnnotation(SahrAnnotationVocabulary.TEMPORAL_WEIGHT);
        validateNumericAnnotation(SahrAnnotationVocabulary.EVIDENCE_WEIGHT);
        validateTemplateAnnotation(SahrAnnotationVocabulary.ANSWER_TEMPLATE, false);
        validateTemplateAnnotation(SahrAnnotationVocabulary.ANSWER_TEMPLATE_TRUE, true);
        validateTemplateAnnotation(SahrAnnotationVocabulary.ANSWER_TEMPLATE_FALSE, true);
        validateAnnotationConflicts();
        validateAliasCollisions();
    }

    private void validateNumericAnnotation(String annotationIri) {
        OWLAnnotationProperty property = ontology.getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create(annotationIri));
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            if (!axiom.getProperty().equals(property)) {
                continue;
            }
            if (!axiom.getValue().isLiteral()) {
                continue;
            }
            String literal = axiom.getValue().asLiteral().map(lit -> lit.getLiteral()).orElse("");
            try {
                Double.parseDouble(literal);
            } catch (NumberFormatException ex) {
                logger.warning("Invalid numeric annotation value '" + literal
                        + "' for " + annotationIri + " on " + axiom.getSubject());
            }
        }
    }

    private void validateTemplateAnnotation(String annotationIri, boolean requireSubject) {
        OWLAnnotationProperty property = ontology.getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create(annotationIri));
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            if (!axiom.getProperty().equals(property)) {
                continue;
            }
            if (!axiom.getValue().isLiteral()) {
                continue;
            }
            String template = axiom.getValue().asLiteral().map(lit -> lit.getLiteral()).orElse("");
            boolean hasSubject = template.contains("{subject}");
            boolean hasObject = template.contains("{object}");
            boolean hasCause = template.contains("{cause}");
            boolean hasEffect = template.contains("{effect}");
            if (requireSubject && !hasSubject) {
                logger.warning("Template missing {subject} for " + annotationIri + " on " + axiom.getSubject());
                continue;
            }
            if (!hasSubject && !hasObject && !hasCause && !hasEffect) {
                logger.warning("Template missing placeholders for " + annotationIri + " on " + axiom.getSubject());
            }
        }
    }

    private void validateAnnotationConflicts() {
        Map<String, Map<String, Set<String>>> values = new HashMap<>();
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            if (!axiom.getValue().isLiteral()) {
                continue;
            }
            String property = axiom.getProperty().getIRI().toString();
            if (!property.startsWith(SahrAnnotationVocabulary.NAMESPACE)) {
                continue;
            }
            String subject = axiom.getSubject().toString();
            String literal = axiom.getValue().asLiteral().map(lit -> lit.getLiteral()).orElse("");
            values.computeIfAbsent(subject, key -> new HashMap<>())
                    .computeIfAbsent(property, key -> new HashSet<>())
                    .add(literal);
        }
        for (Map.Entry<String, Map<String, Set<String>>> entry : values.entrySet()) {
            String subject = entry.getKey();
            for (Map.Entry<String, Set<String>> prop : entry.getValue().entrySet()) {
                if (prop.getValue().size() > 1) {
                    logger.warning("Conflicting annotation values for " + prop.getKey()
                            + " on " + subject + ": " + prop.getValue());
                }
            }
        }
    }

    private void validateAliasCollisions() {
        Map<String, Set<String>> tokenToIri = new HashMap<>();
        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (cls.getIRI().toString().startsWith(SAHR_NAMESPACE)) {
                addLabelTokens(tokenToIri, cls.getIRI());
            }
        }
        for (OWLNamedIndividual individual : ontology.getIndividualsInSignature()) {
            if (individual.getIRI().toString().startsWith(SAHR_NAMESPACE)) {
                addLabelTokens(tokenToIri, individual.getIRI());
            }
        }
        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            if (property.getIRI().toString().startsWith(SAHR_NAMESPACE)) {
                addLabelTokens(tokenToIri, property.getIRI());
            }
        }
        for (Map.Entry<String, Set<String>> entry : tokenToIri.entrySet()) {
            if (entry.getValue().size() > 1) {
                logger.warning("Alias collision for token '" + entry.getKey() + "' -> " + entry.getValue());
            }
        }
    }

    private void addLabelTokens(Map<String, Set<String>> tokenToIri, IRI iri) {
        for (String label : labelsForIri(iri)) {
            String token = normalizeLabelToToken(label);
            if (token.isBlank()) {
                continue;
            }
            tokenToIri.computeIfAbsent(token, key -> new HashSet<>()).add(iri.toString());
        }
    }

    private Set<String> labelsForIri(IRI iri) {
        Set<String> labels = new HashSet<>();
        OWLAnnotationProperty rdfsLabel = ontology.getOWLOntologyManager().getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2000/01/rdf-schema#label"));
        OWLAnnotationProperty skosPrefLabel = ontology.getOWLOntologyManager().getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));
        OWLAnnotationProperty skosAltLabel = ontology.getOWLOntologyManager().getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel"));
        OWLAnnotationProperty ontolexWrittenRep = ontology.getOWLOntologyManager().getOWLDataFactory()
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

    private String normalizeLabelToToken(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }
}
