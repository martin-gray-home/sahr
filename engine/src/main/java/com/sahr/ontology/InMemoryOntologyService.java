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
}
