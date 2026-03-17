package com.sahr.core;

import com.sahr.nlp.TermMapper;
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

    public String resolvePredicate(String predicate,
                                   TermMapper termMapper,
                                   OntologyService ontology,
                                   java.util.function.Function<String, String> lemmatizer) {
        if (predicate == null || predicate.isBlank()) {
            return predicate;
        }
        if (termMapper != null) {
            Optional<String> mapped = termMapper.mapPredicateToken(predicate);
            if (mapped.isPresent()) {
                return mapped.get();
            }
        }
        if (isIri(predicate)) {
            return predicate;
        }
        if (ontology != null) {
            Optional<String> ontologyPredicate = resolvePredicateIri(predicate, ontology);
            if (ontologyPredicate.isPresent()) {
                return ontologyPredicate.get();
            }
        }
        if (lemmatizer != null) {
            String lemma = lemmatizer.apply(predicate);
            if (lemma != null && !lemma.isBlank() && !lemma.equals(predicate)) {
                if (termMapper != null) {
                    Optional<String> mappedLemma = termMapper.mapPredicateToken(lemma);
                    if (mappedLemma.isPresent()) {
                        return mappedLemma.get();
                    }
                }
                if (ontology != null) {
                    Optional<String> ontologyLemma = resolvePredicateIri(lemma, ontology);
                    if (ontologyLemma.isPresent()) {
                        return ontologyLemma.get();
                    }
                }
                return lemma;
            }
        }
        return predicate;
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

    private Optional<String> resolvePredicateIri(String predicate, OntologyService ontology) {
        if (predicate == null || predicate.isBlank() || ontology == null) {
            return Optional.empty();
        }
        java.util.Set<String> direct = ontology.getObjectPropertiesByLabel(predicate);
        if (!direct.isEmpty()) {
            return Optional.of(selectBestPredicateIri(predicate, direct));
        }
        String spaced = predicate.replace('_', ' ');
        if (!spaced.equals(predicate)) {
            java.util.Set<String> spacedMatches = ontology.getObjectPropertiesByLabel(spaced);
            if (!spacedMatches.isEmpty()) {
                return Optional.of(selectBestPredicateIri(predicate, spacedMatches));
            }
        }
        return Optional.empty();
    }

    private String selectBestPredicateIri(String predicate, java.util.Set<String> iris) {
        if (iris == null || iris.isEmpty()) {
            return predicate;
        }
        String normalized = normalizeLabelToToken(predicate);
        String preferred = null;
        int preferredLength = Integer.MAX_VALUE;
        for (String iri : iris) {
            if (iri == null || iri.isBlank()) {
                continue;
            }
            String local = normalizeLabelToToken(localName(iri));
            if (local.equals(normalized)) {
                return iri;
            }
            if (local.equals(normalized + "s")) {
                if (preferred == null || local.length() < preferredLength) {
                    preferred = iri;
                    preferredLength = local.length();
                }
                continue;
            }
            if (local.startsWith(normalized) && local.length() < preferredLength) {
                preferred = iri;
                preferredLength = local.length();
            }
        }
        if (preferred != null) {
            return preferred;
        }
        return iris.stream().sorted().findFirst().orElse(predicate);
    }

    private String normalizeLabelToToken(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private String localName(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return "";
        }
        int hashIdx = predicate.lastIndexOf('#');
        int slashIdx = predicate.lastIndexOf('/');
        int idx = Math.max(hashIdx, slashIdx);
        String local = idx >= 0 ? predicate.substring(idx + 1) : predicate;
        return local.toLowerCase(java.util.Locale.ROOT);
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
