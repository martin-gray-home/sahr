package com.sahr.ontology;

import com.sahr.core.OntologyService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CompositeOntologyService implements OntologyService {
    private final List<OntologyService> delegates;

    public CompositeOntologyService(List<OntologyService> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public boolean isSubclassOf(String child, String parent) {
        return delegates.stream().anyMatch(service -> service.isSubclassOf(child, parent));
    }

    @Override
    public boolean isSymmetricProperty(String property) {
        return delegates.stream().anyMatch(service -> service.isSymmetricProperty(property));
    }

    @Override
    public boolean isTransitiveProperty(String property) {
        return delegates.stream().anyMatch(service -> service.isTransitiveProperty(property));
    }

    @Override
    public Optional<String> getInverseProperty(String property) {
        for (OntologyService service : delegates) {
            Optional<String> candidate = service.getInverseProperty(property);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getSuperclasses(String concept) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getSuperclasses(concept));
        }
        return results;
    }

    @Override
    public Set<String> getSubclasses(String concept) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getSubclasses(concept));
        }
        return results;
    }

    @Override
    public Set<String> getSubproperties(String property) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getSubproperties(property));
        }
        return results;
    }

    @Override
    public Set<String> getObjectPropertyRanges(String property) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getObjectPropertyRanges(property));
        }
        return results;
    }

    @Override
    public Set<String> getObjectPropertiesByLabel(String label) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getObjectPropertiesByLabel(label));
        }
        return results;
    }

    @Override
    public Set<String> getEntityIrisByLabel(String label) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getEntityIrisByLabel(label));
        }
        return results;
    }

    @Override
    public Set<String> getLabels(String iri) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getLabels(iri));
        }
        return results;
    }

    @Override
    public Optional<String> getAnnotationValue(String iri, String annotationIri) {
        for (OntologyService service : delegates) {
            Optional<String> value = service.getAnnotationValue(iri, annotationIri);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getEntitiesWithAnnotation(annotationIri, value));
        }
        return results;
    }

    @Override
    public Set<String> getObjectPropertyTargets(String subjectIri, String propertyIri) {
        Set<String> results = new LinkedHashSet<>();
        for (OntologyService service : delegates) {
            results.addAll(service.getObjectPropertyTargets(subjectIri, propertyIri));
        }
        return results;
    }
}
