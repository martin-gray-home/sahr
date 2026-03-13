package com.sahr.agent;

import com.sahr.core.OntologyService;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class OntologyAnnotationResolver {
    private final OntologyService ontology;

    OntologyAnnotationResolver(OntologyService ontology) {
        this.ontology = ontology;
    }

    Optional<String> resolveObjectPropertyIri(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return Optional.empty();
        }
        if (isIri(predicate)) {
            return Optional.of(predicate);
        }
        Set<String> direct = ontology.getObjectPropertiesByLabel(predicate);
        if (!direct.isEmpty()) {
            return direct.stream().findFirst();
        }
        String spaced = predicate.replace('_', ' ');
        if (!spaced.equals(predicate)) {
            Set<String> spacedMatches = ontology.getObjectPropertiesByLabel(spaced);
            if (!spacedMatches.isEmpty()) {
                return spacedMatches.stream().findFirst();
            }
        }
        return Optional.empty();
    }

    Optional<String> annotationValue(String iri, String annotationIri) {
        return ontology.getAnnotationValue(iri, annotationIri);
    }

    Optional<Double> annotationDouble(String iri, String annotationIri) {
        Optional<String> value = annotationValue(iri, annotationIri);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.get()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    Set<String> entityIrisByLabel(String label) {
        return ontology.getEntityIrisByLabel(label);
    }

    Set<String> labelsForIri(String iri) {
        return ontology.getLabels(iri);
    }

    String normalizeLabelToToken(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    Set<String> entitiesWithAnnotation(String annotationIri, String value) {
        return ontology.getEntitiesWithAnnotation(annotationIri, value);
    }

    private boolean isIri(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
