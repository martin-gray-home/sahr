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
    private static final Map<String, String> CANONICAL_SYNSET_OVERRIDES = Map.ofEntries(
            Map.entry("person", "https://en-word.net/id/oewn-00007846-n"),
            Map.entry("people", "https://en-word.net/id/oewn-00007846-n"),
            Map.entry("human", "https://en-word.net/id/oewn-00007846-n"),
            Map.entry("man", "https://en-word.net/id/oewn-10306910-n"),
            Map.entry("woman", "https://en-word.net/id/oewn-10807146-n"),
            Map.entry("boy", "https://en-word.net/id/oewn-10305010-n"),
            Map.entry("girl", "https://en-word.net/id/oewn-10104064-n"),
            Map.entry("hat", "https://en-word.net/id/oewn-03502782-n"),
            Map.entry("house", "https://en-word.net/id/oewn-03549540-n"),
            Map.entry("chair", "https://en-word.net/id/oewn-03005231-n"),
            Map.entry("table", "https://en-word.net/id/oewn-04386330-n"),
            Map.entry("room", "https://en-word.net/id/oewn-04112987-n"),
            Map.entry("floor", "https://en-word.net/id/oewn-03370438-n"),
            Map.entry("box", "https://en-word.net/id/oewn-02886585-n"),
            Map.entry("bed", "https://en-word.net/id/oewn-02821967-n"),
            Map.entry("door", "https://en-word.net/id/oewn-03226423-n"),
            Map.entry("window", "https://en-word.net/id/oewn-04594951-n"),
            Map.entry("ceiling", "https://en-word.net/id/oewn-02993828-n"),
            Map.entry("wall", "https://en-word.net/id/oewn-04554141-n")
    );

    private final Map<String, String> labelToEntityIri = new ConcurrentHashMap<>();
    private final Map<String, String> labelToPropertyIri = new ConcurrentHashMap<>();
    private final Map<String, String> synsetOverrides = new ConcurrentHashMap<>();

    public LabelLexicalMapper(OWLOntology ontology) {
        for (Map.Entry<String, String> entry : CANONICAL_SYNSET_OVERRIDES.entrySet()) {
            IRI iri = IRI.create(entry.getValue());
            if (ontology.containsEntityInSignature(iri)) {
                synsetOverrides.put(entry.getKey(), entry.getValue());
            }
        }
        indexAnnotations(ontology);
        indexDataProperties(ontology);
        indexObjectPropertyLabels(ontology);
    }

    @Override
    public Optional<String> mapToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(token);
        String override = synsetOverrides.get(normalized);
        if (override != null) {
            return Optional.of(override);
        }
        return Optional.ofNullable(labelToEntityIri.get(normalized));
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
                    boolean isObjectProperty = ontology.containsObjectPropertyInSignature(subjectIri);
                    if (!isObjectProperty) {
                        String iri = subjectIri.toString();
                        labelToEntityIri.merge(key, iri, this::preferEntityIri);
                    }
                    if (isObjectProperty) {
                        labelToPropertyIri.putIfAbsent(key, subjectIri.toString());
                    }
                });
            }
        }
    }

    private String preferEntityIri(String existing, String candidate) {
        if (existing == null) {
            return candidate;
        }
        boolean existingPreferred = isPreferredEntity(existing);
        boolean candidatePreferred = isPreferredEntity(candidate);
        if (!existingPreferred && candidatePreferred) {
            return candidate;
        }
        return existing;
    }

    private boolean isPreferredEntity(String iri) {
        return iri != null && iri.startsWith("https://sahr.ai/");
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
