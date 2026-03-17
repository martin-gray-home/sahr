package com.sahr.ontology;

import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.semantic.model.InferencePolicy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CompositeOntologyService implements OntologyService, PropertyPolicyProvider {
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

    @Override
    public Optional<InferencePolicy> inversePolicy(String propertyIri) {
        return policyFromDelegates(provider -> provider.inversePolicy(propertyIri));
    }

    @Override
    public Optional<InferencePolicy> symmetricPolicy(String propertyIri) {
        return policyFromDelegates(provider -> provider.symmetricPolicy(propertyIri));
    }

    @Override
    public Optional<InferencePolicy> transitivePolicy(String propertyIri) {
        return policyFromDelegates(provider -> provider.transitivePolicy(propertyIri));
    }

    @Override
    public Optional<String> inverseProperty(String propertyIri) {
        for (OntologyService service : delegates) {
            if (service instanceof PropertyPolicyProvider provider) {
                Optional<String> candidate = provider.inverseProperty(propertyIri);
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<InferencePolicy> policyFromDelegates(
            java.util.function.Function<PropertyPolicyProvider, Optional<InferencePolicy>> lookup) {
        for (OntologyService service : delegates) {
            if (service instanceof PropertyPolicyProvider provider) {
                Optional<InferencePolicy> candidate = lookup.apply(provider);
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
        }
        return Optional.empty();
    }
}
