package com.sahr.core;

import com.sahr.semantic.model.InferencePolicy;

import java.util.Optional;

public interface PropertyPolicyProvider {
    Optional<InferencePolicy> inversePolicy(String propertyIri);

    Optional<InferencePolicy> symmetricPolicy(String propertyIri);

    Optional<InferencePolicy> transitivePolicy(String propertyIri);

    Optional<String> inverseProperty(String propertyIri);
}
