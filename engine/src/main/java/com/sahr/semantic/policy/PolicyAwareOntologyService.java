package com.sahr.semantic.policy;

import com.sahr.core.OntologyService;
import com.sahr.core.PropertyPolicyProvider;
import com.sahr.semantic.model.InferencePolicy;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PolicyAwareOntologyService implements OntologyService, PropertyPolicyProvider {
    private final OntologyService delegate;
    private final PropertyPolicyRegistry policyRegistry;

    public PolicyAwareOntologyService(OntologyService delegate, PropertyPolicyRegistry policyRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
    }

    @Override
    public boolean isSubclassOf(String child, String parent) {
        return delegate.isSubclassOf(child, parent);
    }

    @Override
    public boolean isSymmetricProperty(String property) {
        return policyRegistry.symmetricPolicy(property).isPresent();
    }

    @Override
    public boolean isTransitiveProperty(String property) {
        return policyRegistry.transitivePolicy(property).isPresent();
    }

    @Override
    public Optional<String> getInverseProperty(String property) {
        return policyRegistry.inverseProperty(property);
    }

    @Override
    public Set<String> getSuperclasses(String concept) {
        return delegate.getSuperclasses(concept);
    }

    @Override
    public Set<String> getSubclasses(String concept) {
        return delegate.getSubclasses(concept);
    }

    @Override
    public Set<String> getSubproperties(String property) {
        return delegate.getSubproperties(property);
    }

    @Override
    public Set<String> getObjectPropertyRanges(String property) {
        return delegate.getObjectPropertyRanges(property);
    }

    @Override
    public Set<String> getObjectPropertiesByLabel(String label) {
        return delegate.getObjectPropertiesByLabel(label);
    }

    @Override
    public Set<String> getEntityIrisByLabel(String label) {
        return delegate.getEntityIrisByLabel(label);
    }

    @Override
    public Set<String> getLabels(String iri) {
        return delegate.getLabels(iri);
    }

    @Override
    public Optional<String> getAnnotationValue(String iri, String annotationIri) {
        return delegate.getAnnotationValue(iri, annotationIri);
    }

    @Override
    public Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
        return delegate.getEntitiesWithAnnotation(annotationIri, value);
    }

    @Override
    public Set<String> getObjectPropertyTargets(String subjectIri, String propertyIri) {
        return delegate.getObjectPropertyTargets(subjectIri, propertyIri);
    }

    @Override
    public Optional<InferencePolicy> inversePolicy(String propertyIri) {
        return policyRegistry.inversePolicy(propertyIri);
    }

    @Override
    public Optional<InferencePolicy> symmetricPolicy(String propertyIri) {
        return policyRegistry.symmetricPolicy(propertyIri);
    }

    @Override
    public Optional<InferencePolicy> transitivePolicy(String propertyIri) {
        return policyRegistry.transitivePolicy(propertyIri);
    }

    @Override
    public Optional<String> inverseProperty(String propertyIri) {
        return policyRegistry.inverseProperty(propertyIri);
    }
}
