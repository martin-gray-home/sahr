package com.sahr.ontology;

import com.sahr.nlp.TermMapper;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

public final class VectorLexicalMapper implements TermMapper {
    private static final Logger logger = Logger.getLogger(VectorLexicalMapper.class.getName());
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String OBO_EXACT_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym";
    private static final String OBO_RELATED_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym";
    private static final String OBO_BROAD_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym";
    private static final String OBO_NARROW_SYNONYM = "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym";
    private static final int DEFAULT_DIMENSIONS = 256;
    private static final double DEFAULT_THRESHOLD = 0.6;

    private final VectorIndex entityIndex = new VectorIndex();
    private final VectorIndex propertyIndex = new VectorIndex();
    private final TextVectorizer vectorizer;
    private final double threshold;
    private final Map<String, Double> labelWeights = new HashMap<>();

    public VectorLexicalMapper(OWLOntology ontology) {
        this(ontology, new HashingVectorizer(DEFAULT_DIMENSIONS), DEFAULT_THRESHOLD);
    }

    public VectorLexicalMapper(OWLOntology ontology, TextVectorizer vectorizer, double threshold) {
        this.vectorizer = vectorizer == null ? new HashingVectorizer(DEFAULT_DIMENSIONS) : vectorizer;
        this.threshold = threshold;
        labelWeights.put(RDFS_LABEL, 1.0);
        labelWeights.put(SKOS_PREF_LABEL, 1.0);
        labelWeights.put(SKOS_ALT_LABEL, 0.8);
        labelWeights.put(OBO_EXACT_SYNONYM, 0.9);
        labelWeights.put(OBO_RELATED_SYNONYM, 0.6);
        labelWeights.put(OBO_BROAD_SYNONYM, 0.6);
        labelWeights.put(OBO_NARROW_SYNONYM, 0.6);
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
        Map<IRI, List<LabeledText>> labelsByIri = collectLabels(ontology);
        Map<IRI, List<LabeledText>> orderedLabels = new TreeMap<>(Comparator.comparing(IRI::toString));
        orderedLabels.putAll(labelsByIri);
        UnionFind<IRI> union = buildEquivalenceClusters(ontology, orderedLabels.keySet());

        Map<IRI, float[]> centroidByIri = new HashMap<>();
        for (Map.Entry<IRI, List<LabeledText>> entry : orderedLabels.entrySet()) {
            IRI iri = entry.getKey();
            IRI root = union.find(iri);
            if (root == null) {
                root = iri;
            }
            centroidByIri.computeIfAbsent(root, ignored -> new float[vectorizer.dimensions()]);
            float[] centroid = centroidByIri.get(root);
            for (LabeledText label : entry.getValue()) {
                float[] vector = vectorizer.vectorize(label.text);
                double weight = label.weight;
                for (int i = 0; i < centroid.length; i++) {
                    centroid[i] += vector[i] * weight;
                }
            }
        }
        normalizeCentroids(centroidByIri);

        for (Map.Entry<IRI, List<LabeledText>> entry : orderedLabels.entrySet()) {
            IRI iri = entry.getKey();
            IRI root = union.find(iri);
            if (root == null) {
                root = iri;
            }
            float[] centroid = centroidByIri.get(root);
            if (centroid == null) {
                continue;
            }
            for (LabeledText label : entry.getValue()) {
                if (ontology.containsObjectPropertyInSignature(iri)
                        || ontology.containsDataPropertyInSignature(iri)) {
                    propertyIndex.add(new VectorIndex.Entry(iri.toString(), label.text, centroid));
                } else {
                    entityIndex.add(new VectorIndex.Entry(iri.toString(), label.text, centroid));
                }
            }
        }
    }

    private boolean isLabelProperty(IRI iri) {
        String value = iri.toString();
        return labelWeights.containsKey(value);
    }

    private String normalize(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.replaceAll("\\s+", "_");
    }

    private Map<IRI, List<LabeledText>> collectLabels(OWLOntology ontology) {
        Map<IRI, List<LabeledText>> labelsByIri = new HashMap<>();
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
            Double weight = labelWeights.get(propertyIri.toString());
            if (weight == null) {
                continue;
            }
            axiom.getSubject().asIRI().ifPresent(subject -> {
                labelsByIri.computeIfAbsent(subject, ignored -> new ArrayList<>())
                        .add(new LabeledText(normalized, weight));
            });
        }
        return labelsByIri;
    }

    private UnionFind<IRI> buildEquivalenceClusters(OWLOntology ontology, Set<IRI> candidates) {
        UnionFind<IRI> union = new UnionFind<>(candidates);
        for (var axiom : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
            List<OWLClass> classes = axiom.getClassesInSignature().stream().toList();
            if (classes.size() < 2) {
                continue;
            }
            IRI first = classes.get(0).getIRI();
            for (int i = 1; i < classes.size(); i++) {
                union.union(first, classes.get(i).getIRI());
            }
        }
        for (var axiom : ontology.getAxioms(AxiomType.EQUIVALENT_OBJECT_PROPERTIES)) {
            List<OWLObjectProperty> props = axiom.getObjectPropertiesInSignature().stream().toList();
            if (props.size() < 2) {
                continue;
            }
            IRI first = props.get(0).getIRI();
            for (int i = 1; i < props.size(); i++) {
                union.union(first, props.get(i).getIRI());
            }
        }
        for (var axiom : ontology.getAxioms(AxiomType.SAME_INDIVIDUAL)) {
            if (!(axiom instanceof OWLSameIndividualAxiom)) {
                continue;
            }
            List<IRI> iris = axiom.getIndividualsInSignature().stream().map(OWLEntity::getIRI).toList();
            if (iris.size() < 2) {
                continue;
            }
            IRI first = iris.get(0);
            for (int i = 1; i < iris.size(); i++) {
                union.union(first, iris.get(i));
            }
        }
        return union;
    }

    private void normalizeCentroids(Map<IRI, float[]> centroids) {
        for (float[] vector : centroids.values()) {
            double sum = 0.0;
            for (float v : vector) {
                sum += v * v;
            }
            if (sum == 0.0) {
                continue;
            }
            double norm = Math.sqrt(sum);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
    }

    private static final class LabeledText {
        private final String text;
        private final double weight;

        private LabeledText(String text, double weight) {
            this.text = text;
            this.weight = weight;
        }
    }

    private static final class UnionFind<T> {
        private final Map<T, T> parent = new HashMap<>();

        private UnionFind(Set<T> elements) {
            for (T element : elements) {
                parent.put(element, element);
            }
        }

        private T find(T value) {
            if (value == null) {
                return null;
            }
            T root = parent.get(value);
            if (root == null) {
                parent.put(value, value);
                return value;
            }
            if (root.equals(value)) {
                return root;
            }
            T resolved = find(root);
            parent.put(value, resolved);
            return resolved;
        }

        private void union(T left, T right) {
            if (left == null || right == null) {
                return;
            }
            T rootLeft = find(left);
            T rootRight = find(right);
            if (rootLeft == null || rootRight == null || rootLeft.equals(rootRight)) {
                return;
            }
            parent.put(rootRight, rootLeft);
        }
    }
}
