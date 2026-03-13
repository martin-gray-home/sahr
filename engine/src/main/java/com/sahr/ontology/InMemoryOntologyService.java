package com.sahr.ontology;

import com.sahr.core.OntologyService;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryOntologyService implements OntologyService {
    private final Map<String, Set<String>> subclassMap = new HashMap<>();
    private final Set<String> symmetricProperties = new HashSet<>();
    private final Set<String> transitiveProperties = new HashSet<>();
    private final Map<String, String> inverseProperties = new HashMap<>();
    private final Map<String, Set<String>> propertyRanges = new HashMap<>();
    private final Map<String, Set<String>> subproperties = new HashMap<>();
    private final Map<String, Set<String>> propertyLabels = new HashMap<>();
    private final Map<String, Set<String>> entityLabels = new HashMap<>();
    private final Map<String, Set<String>> labelsByIri = new HashMap<>();
    private final Map<String, Map<String, String>> annotations = new HashMap<>();

    public void addSubclass(String child, String parent) {
        subclassMap.computeIfAbsent(child, key -> new HashSet<>()).add(parent);
    }

    public void addSymmetricProperty(String property) {
        symmetricProperties.add(property);
    }

    public void addTransitiveProperty(String property) {
        transitiveProperties.add(property);
    }

    public void addInverseProperty(String property, String inverse) {
        inverseProperties.put(property, inverse);
        inverseProperties.put(inverse, property);
    }

    public void addPropertyRange(String property, String range) {
        propertyRanges.computeIfAbsent(property, key -> new HashSet<>()).add(range);
    }

    public void addSubproperty(String child, String parent) {
        subproperties.computeIfAbsent(parent, key -> new HashSet<>()).add(child);
    }

    public void addObjectPropertyLabel(String label, String iri) {
        propertyLabels.computeIfAbsent(label, key -> new HashSet<>()).add(iri);
    }

    public void addEntityLabel(String label, String iri) {
        entityLabels.computeIfAbsent(label, key -> new HashSet<>()).add(iri);
    }

    public void addLabelForIri(String iri, String label) {
        labelsByIri.computeIfAbsent(iri, key -> new HashSet<>()).add(label);
    }

    public void addAnnotation(String iri, String annotationIri, String value) {
        annotations.computeIfAbsent(iri, key -> new HashMap<>()).put(annotationIri, value);
    }

    @Override
    public boolean isSubclassOf(String child, String parent) {
        return subclassMap.getOrDefault(child, Collections.emptySet()).contains(parent);
    }

    @Override
    public boolean isSymmetricProperty(String property) {
        return symmetricProperties.contains(property);
    }

    @Override
    public boolean isTransitiveProperty(String property) {
        return transitiveProperties.contains(property);
    }

    @Override
    public Optional<String> getInverseProperty(String property) {
        return Optional.ofNullable(inverseProperties.get(property));
    }

    @Override
    public Set<String> getSuperclasses(String concept) {
        return Collections.unmodifiableSet(subclassMap.getOrDefault(concept, Collections.emptySet()));
    }

    @Override
    public Set<String> getSubclasses(String concept) {
        Set<String> subclasses = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : subclassMap.entrySet()) {
            if (entry.getValue().contains(concept)) {
                subclasses.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(subclasses);
    }

    @Override
    public Set<String> getSubproperties(String property) {
        return Collections.unmodifiableSet(subproperties.getOrDefault(property, Collections.emptySet()));
    }

    @Override
    public Set<String> getObjectPropertyRanges(String property) {
        return Collections.unmodifiableSet(propertyRanges.getOrDefault(property, Collections.emptySet()));
    }

    @Override
    public Set<String> getObjectPropertiesByLabel(String label) {
        return Collections.unmodifiableSet(propertyLabels.getOrDefault(label, Collections.emptySet()));
    }

    @Override
    public Set<String> getEntityIrisByLabel(String label) {
        return Collections.unmodifiableSet(entityLabels.getOrDefault(label, Collections.emptySet()));
    }

    @Override
    public Set<String> getLabels(String iri) {
        return Collections.unmodifiableSet(labelsByIri.getOrDefault(iri, Collections.emptySet()));
    }

    @Override
    public Optional<String> getAnnotationValue(String iri, String annotationIri) {
        return Optional.ofNullable(annotations.getOrDefault(iri, Collections.emptyMap()).get(annotationIri));
    }

    @Override
    public Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
        Set<String> results = new HashSet<>();
        if (annotationIri == null || value == null) {
            return results;
        }
        for (Map.Entry<String, Map<String, String>> entry : annotations.entrySet()) {
            String annotationValue = entry.getValue().get(annotationIri);
            if (annotationValue != null && annotationValue.equals(value)) {
                results.add(entry.getKey());
            }
        }
        return results;
    }
}
