package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentInput;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.PropertySemantics;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticNodeType;
import com.sahr.semantic.model.SemanticSourceReference;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.AxiomType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class OwlSemanticImporter {
    private static final String UNKNOWN_FAMILY = "unknown";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String ONTOLEX_WRITTEN_REP = "http://www.w3.org/ns/lemon/ontolex#writtenRep";

    public OwlImportResult importOntology(OWLOntology ontology, String sourceName) {
        Objects.requireNonNull(ontology, "ontology");
        Objects.requireNonNull(sourceName, "sourceName");

        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        OwlLabelResolver labels = new OwlLabelResolver(ontology, manager);

        List<SemanticNode> nodes = new ArrayList<>();
        int missingClassLabels = 0;
        int missingPropertyLabels = 0;
        List<PropertySemantics> propertySemantics = new ArrayList<>();

        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (cls.isOWLThing() || cls.isOWLNothing()) {
                continue;
            }
            LabelResult label = labels.resolve(cls.getIRI());
            if (label.fallbackUsed) {
                missingClassLabels++;
            }
            nodes.add(buildNode(
                    "concept:",
                    cls.getIRI(),
                    label.label,
                    sourceName,
                    SemanticNodeType.CONCEPT
            ));
        }

        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            if (property.isOWLTopObjectProperty() || property.isOWLBottomObjectProperty()) {
                continue;
            }
            LabelResult label = labels.resolve(property.getIRI());
            if (label.fallbackUsed) {
                missingPropertyLabels++;
            }
            nodes.add(buildNode(
                    "relation:",
                    property.getIRI(),
                    label.label,
                    sourceName,
                    SemanticNodeType.RELATION
            ));
            propertySemantics.add(buildPropertySemantics(ontology, property, label.label, sourceName));
        }

        AlignmentInput input = new AlignmentInput(nodes, List.of(), List.of(), propertySemantics);
        OwlImportReport report = new OwlImportReport(
                (int) ontology.getClassesInSignature().stream()
                        .filter(cls -> !cls.isOWLThing() && !cls.isOWLNothing())
                        .count(),
                (int) ontology.getObjectPropertiesInSignature().stream()
                        .filter(prop -> !prop.isOWLTopObjectProperty() && !prop.isOWLBottomObjectProperty())
                        .count(),
                missingClassLabels,
                missingPropertyLabels
        );
        return new OwlImportResult(input, report);
    }

    private SemanticNode buildNode(String prefix,
                                   IRI iri,
                                   String label,
                                   String sourceName,
                                   SemanticNodeType type) {
        SemanticSourceReference source = new SemanticSourceReference(
                sourceName,
                iri.toString(),
                label,
                1.0
        );
        return new SemanticNode(
                prefix + iri,
                label,
                UNKNOWN_FAMILY,
                type,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(source)
        );
    }

    private PropertySemantics buildPropertySemantics(OWLOntology ontology,
                                                     OWLObjectProperty property,
                                                     String label,
                                                     String sourceName) {
        List<String> domains = ontology.getObjectPropertyDomainAxioms(property).stream()
                .map(ax -> ax.getDomain())
                .filter(domain -> !domain.isAnonymous())
                .map(domain -> domain.asOWLClass().getIRI().toString())
                .distinct()
                .toList();

        List<String> ranges = ontology.getObjectPropertyRangeAxioms(property).stream()
                .map(ax -> ax.getRange())
                .filter(range -> !range.isAnonymous())
                .map(range -> range.asOWLClass().getIRI().toString())
                .distinct()
                .toList();

        List<String> inverseIris = ontology.getAxioms(AxiomType.INVERSE_OBJECT_PROPERTIES).stream()
                .filter(ax -> ax.getProperties().contains(property))
                .flatMap(ax -> ax.getProperties().stream())
                .filter(expr -> !expr.isAnonymous())
                .map(expr -> expr.asOWLObjectProperty().getIRI().toString())
                .filter(iri -> !iri.equals(property.getIRI().toString()))
                .distinct()
                .toList();

        boolean symmetric = ontology.getAxioms(AxiomType.SYMMETRIC_OBJECT_PROPERTY).stream()
                .anyMatch(ax -> ax.getProperty().equals(property));

        boolean transitive = ontology.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY).stream()
                .anyMatch(ax -> ax.getProperty().equals(property));

        SemanticSourceReference source = new SemanticSourceReference(
                sourceName,
                property.getIRI().toString(),
                label,
                1.0
        );

        return new PropertySemantics(
                property.getIRI().toString(),
                domains,
                ranges,
                inverseIris,
                symmetric,
                transitive,
                List.of(source)
        );
    }

    private static final class LabelResult {
        private final String label;
        private final boolean fallbackUsed;

        private LabelResult(String label, boolean fallbackUsed) {
            this.label = label;
            this.fallbackUsed = fallbackUsed;
        }
    }

    private static final class OwlLabelResolver {
        private final OWLOntology ontology;
        private final OWLAnnotationProperty rdfsLabel;
        private final OWLAnnotationProperty skosPrefLabel;
        private final OWLAnnotationProperty skosAltLabel;
        private final OWLAnnotationProperty ontolexWrittenRep;

        private OwlLabelResolver(OWLOntology ontology, OWLOntologyManager manager) {
            this.ontology = ontology;
            this.rdfsLabel = manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create(RDFS_LABEL));
            this.skosPrefLabel = manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_PREF_LABEL));
            this.skosAltLabel = manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_ALT_LABEL));
            this.ontolexWrittenRep = manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create(ONTOLEX_WRITTEN_REP));
        }

        private LabelResult resolve(IRI iri) {
            Optional<String> label = preferredLabel(iri);
            if (label.isPresent()) {
                return new LabelResult(label.get(), false);
            }
            return new LabelResult(fallbackLabel(iri), true);
        }

        private Optional<String> preferredLabel(IRI iri) {
            List<OWLAnnotationProperty> preference = List.of(rdfsLabel, skosPrefLabel, skosAltLabel, ontolexWrittenRep);
            for (OWLAnnotationProperty property : preference) {
                Optional<String> label = labelFor(iri, property);
                if (label.isPresent()) {
                    return label;
                }
            }
            return Optional.empty();
        }

        private Optional<String> labelFor(IRI iri, OWLAnnotationProperty property) {
            return ontology.getAnnotationAssertionAxioms(iri).stream()
                    .filter(ax -> ax.getProperty().equals(property))
                    .map(OWLAnnotationAssertionAxiom::getValue)
                    .flatMap(value -> value.asLiteral().stream())
                    .map(literal -> literal.getLiteral())
                    .filter(label -> label != null && !label.isBlank())
                    .min(Comparator.comparingInt(String::length));
        }

        private String fallbackLabel(IRI iri) {
            String shortForm = iri.getShortForm();
            if (shortForm != null && !shortForm.isBlank()) {
                return shortForm;
            }
            return iri.toString();
        }
    }
}
