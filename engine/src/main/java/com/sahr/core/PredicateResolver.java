package com.sahr.core;

import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PredicateResolver {
    private final Map<String, List<String>> predicateAliases;

    public PredicateResolver(Map<String, List<String>> predicateAliases) {
        this.predicateAliases = predicateAliases == null ? Map.of() : predicateAliases;
    }

    public List<PredicateMatch> expandPredicateMatches(String predicate, OntologyService ontology) {
        List<PredicateMatch> expanded = new ArrayList<>();
        expanded.add(new PredicateMatch(predicate, PredicateMatchType.DIRECT, null));
        InferencePolicy symmetricPolicy = policyForSymmetric(ontology, predicate);
        if (symmetricPolicy != null && symmetricPolicy.strength() != InferencePolicyStrength.DISABLED) {
            expanded.add(new PredicateMatch(predicate, PredicateMatchType.SYMMETRIC, symmetricPolicy.strength()));
        } else if (isSymmetricAllowed(ontology, predicate)) {
            expanded.add(new PredicateMatch(predicate, PredicateMatchType.SYMMETRIC, null));
        }
        List<String> aliases = predicateAliases.getOrDefault(predicate, List.of());
        for (String alias : aliases) {
            expanded.add(new PredicateMatch(alias, PredicateMatchType.DIRECT, null));
        }
        if (isIri(predicate)) {
            for (String subproperty : ontology.getSubproperties(predicate)) {
                expanded.add(new PredicateMatch(subproperty, PredicateMatchType.DIRECT, null));
            }
            InferencePolicy inversePolicy = policyForInverse(ontology, predicate);
            inverseProperty(ontology, predicate).ifPresent(inv -> {
                expanded.add(new PredicateMatch(inv, PredicateMatchType.INVERSE,
                        inversePolicy == null ? null : inversePolicy.strength()));
                for (String subproperty : ontology.getSubproperties(inv)) {
                    expanded.add(new PredicateMatch(subproperty, PredicateMatchType.INVERSE,
                            inversePolicy == null ? null : inversePolicy.strength()));
                }
            });
            return expanded;
        }
        java.util.Set<String> locationFamily = HeadOntology.expandFamily(ontology, HeadOntology.LOCATION_TRANSFER);
        if (locationFamily.contains(predicate)) {
            for (String relation : locationFamily) {
                expanded.add(new PredicateMatch(relation, PredicateMatchType.DIRECT, null));
            }
        }
        return expanded;
    }

    private boolean isSymmetricAllowed(OntologyService ontology, String predicate) {
        if (ontology instanceof PropertyPolicyProvider provider) {
            return provider.symmetricPolicy(predicate).isPresent();
        }
        return false;
    }

    private Optional<String> inverseProperty(OntologyService ontology, String predicate) {
        if (ontology instanceof PropertyPolicyProvider provider) {
            return provider.inverseProperty(predicate);
        }
        return Optional.empty();
    }

    private InferencePolicy policyForSymmetric(OntologyService ontology, String predicate) {
        if (ontology instanceof PropertyPolicyProvider provider) {
            return provider.symmetricPolicy(predicate).orElse(null);
        }
        return null;
    }

    private InferencePolicy policyForInverse(OntologyService ontology, String predicate) {
        if (ontology instanceof PropertyPolicyProvider provider) {
            return provider.inversePolicy(predicate).orElse(null);
        }
        return null;
    }

    private boolean isIri(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
