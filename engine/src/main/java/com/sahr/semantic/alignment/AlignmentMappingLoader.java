package com.sahr.semantic.alignment;

import com.sahr.ontology.OntologyLoader;
import com.sahr.semantic.model.AlignmentConfidence;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AlignmentMappingLoader {
    private static final IRI ALIGNED_FAMILY_IRI = IRI.create("https://sahr.ai/ontology/annotations#alignedFamily");
    private static final IRI CONFIDENCE_IRI = IRI.create("https://sahr.ai/ontology/annotations#alignmentConfidence");
    private static final IRI RATIONALE_IRI = IRI.create("https://sahr.ai/ontology/annotations#alignmentRationale");

    private final Map<String, AlignmentRule> rulesBySourceIri;

    private AlignmentMappingLoader(Map<String, AlignmentRule> rulesBySourceIri) {
        this.rulesBySourceIri = rulesBySourceIri;
    }

    public static AlignmentMappingLoader loadFromClasspath(String resourcePath, CanonicalFamilyRegistry registry) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(registry, "registry");
        OWLOntology ontology = OntologyLoader.loadFromClasspath(List.of(resourcePath));
        Map<String, AlignmentRule> rules = new HashMap<>();

        for (OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
            if (!axiom.getProperty().getIRI().equals(ALIGNED_FAMILY_IRI)) {
                continue;
            }

            String subject = axiom.getSubject().asIRI().map(IRI::toString).orElse(null);
            String alignedFamilyIri = asIriString(axiom.getValue()).orElse(null);
            if (subject == null || alignedFamilyIri == null) {
                continue;
            }

            String familyId = registry.familyIdForIri(alignedFamilyIri).orElse(null);
            if (familyId == null) {
                continue;
            }

            AlignmentConfidence confidence = parseConfidence(findLiteral(ontology, axiom.getSubject().asIRI().orElse(null), CONFIDENCE_IRI)
                    .orElse("weak"));
            String rationale = findLiteral(ontology, axiom.getSubject().asIRI().orElse(null), RATIONALE_IRI)
                    .orElse("alignment mapping");

            rules.put(subject, new AlignmentRule(familyId, confidence, rationale));
        }

        return new AlignmentMappingLoader(rules);
    }

    public Optional<AlignmentRule> ruleForSource(String sourceIri) {
        Objects.requireNonNull(sourceIri, "sourceIri");
        return Optional.ofNullable(rulesBySourceIri.get(sourceIri));
    }

    private static Optional<String> asIriString(OWLAnnotationValue value) {
        return value.asIRI().map(IRI::toString);
    }

    private static Optional<String> findLiteral(OWLOntology ontology, IRI subject, IRI property) {
        if (subject == null) {
            return Optional.empty();
        }
        for (OWLAnnotationAssertionAxiom axiom : ontology.getAnnotationAssertionAxioms(subject)) {
            if (!axiom.getProperty().getIRI().equals(property)) {
                continue;
            }
            Optional<String> literal = axiom.getValue().asLiteral().map(lit -> lit.getLiteral());
            if (literal.isPresent()) {
                return literal;
            }
        }
        return Optional.empty();
    }

    private static AlignmentConfidence parseConfidence(String raw) {
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "EXACT" -> AlignmentConfidence.EXACT;
            case "STRONG" -> AlignmentConfidence.STRONG;
            case "WEAK" -> AlignmentConfidence.WEAK;
            case "MANUAL_OVERRIDE" -> AlignmentConfidence.MANUAL_OVERRIDE;
            case "UNRESOLVED" -> AlignmentConfidence.UNRESOLVED;
            default -> AlignmentConfidence.WEAK;
        };
    }
}
