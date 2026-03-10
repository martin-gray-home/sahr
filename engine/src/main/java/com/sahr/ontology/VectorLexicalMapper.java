package com.sahr.ontology;

import com.sahr.nlp.TermMapper;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public final class VectorLexicalMapper implements TermMapper {
    private static final Logger logger = Logger.getLogger(VectorLexicalMapper.class.getName());
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final int DEFAULT_DIMENSIONS = 256;
    private static final double DEFAULT_THRESHOLD = 0.6;

    private final VectorIndex entityIndex = new VectorIndex();
    private final VectorIndex propertyIndex = new VectorIndex();
    private final HashingVectorizer vectorizer;
    private final double threshold;

    public VectorLexicalMapper(OWLOntology ontology) {
        this(ontology, DEFAULT_DIMENSIONS, DEFAULT_THRESHOLD);
    }

    public VectorLexicalMapper(OWLOntology ontology, int dimensions, double threshold) {
        this.vectorizer = new HashingVectorizer(Math.max(64, dimensions));
        this.threshold = threshold;
        buildIndex(ontology);
        logger.info(() -> "VectorLexicalMapper built: entityLabels=" + entityIndex.size()
                + " propertyLabels=" + propertyIndex.size());
    }

    @Override
    public Optional<String> mapToken(String token) {
        return lookup(token, entityIndex);
    }

    @Override
    public Optional<String> mapPredicateToken(String token) {
        return lookup(token, propertyIndex);
    }

    private Optional<String> lookup(String token, VectorIndex index) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        float[] query = vectorizer.vectorize(token);
        double bestScore = -1.0;
        String bestIri = null;
        for (VectorIndex.Entry entry : index.entries()) {
            double score = cosine(query, entry.vector());
            if (score > bestScore) {
                bestScore = score;
                bestIri = entry.iri();
            }
        }
        if (bestIri != null && bestScore >= threshold) {
            return Optional.of(bestIri);
        }
        return Optional.empty();
    }

    private double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private void buildIndex(OWLOntology ontology) {
        if (ontology == null) {
            return;
        }
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            IRI propertyIri = axiom.getProperty().getIRI();
            if (!isLabelProperty(propertyIri)) {
                continue;
            }
            OWLAnnotationValue value = axiom.getValue();
            if (!(value instanceof OWLLiteral)) {
                continue;
            }
            String label = ((OWLLiteral) value).getLiteral();
            String normalized = normalize(label);
            if (normalized.isBlank()) {
                continue;
            }
            axiom.getSubject().asIRI().ifPresent(subject -> {
                float[] vector = vectorizer.vectorize(normalized);
                if (ontology.containsObjectPropertyInSignature(subject)
                        || ontology.containsDataPropertyInSignature(subject)) {
                    propertyIndex.add(new VectorIndex.Entry(subject.toString(), normalized, vector));
                } else {
                    entityIndex.add(new VectorIndex.Entry(subject.toString(), normalized, vector));
                }
            });
        }
    }

    private boolean isLabelProperty(IRI iri) {
        String value = iri.toString();
        return RDFS_LABEL.equals(value) || SKOS_PREF_LABEL.equals(value) || SKOS_ALT_LABEL.equals(value);
    }

    private String normalize(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.replaceAll("\\s+", "_");
    }
}
