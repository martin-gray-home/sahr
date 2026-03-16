package com.sahr.semantic.alignment;

import com.sahr.ontology.OntologyLoader;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CanonicalFamilyRegistry {
    private static final IRI FAMILY_ID_IRI = IRI.create("https://sahr.ai/ontology/annotations#familyId");

    private final Map<String, String> familyIdByIri;

    private CanonicalFamilyRegistry(Map<String, String> familyIdByIri) {
        this.familyIdByIri = familyIdByIri;
    }

    public static CanonicalFamilyRegistry loadFromClasspath(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        OWLOntology ontology = OntologyLoader.loadFromClasspath(List.of(resourcePath));
        Map<String, String> familyIds = new HashMap<>();
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            if (!axiom.getProperty().getIRI().equals(FAMILY_ID_IRI)) {
                continue;
            }
            String subject = axiom.getSubject().asIRI().map(IRI::toString).orElse(null);
            String familyId = axiom.getValue().asLiteral().map(literal -> literal.getLiteral()).orElse(null);
            if (subject == null || familyId == null) {
                continue;
            }
            familyIds.put(subject, familyId);
        }
        return new CanonicalFamilyRegistry(familyIds);
    }

    public Optional<String> familyIdForIri(String iri) {
        Objects.requireNonNull(iri, "iri");
        return Optional.ofNullable(familyIdByIri.get(iri));
    }
}
