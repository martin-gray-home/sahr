package com.sahr.ontology;

import com.sahr.core.OntologyService;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class SemanticTypeCompatibilityService {
    private static final String WORDNET_HYPERNYM = "https://globalwordnet.github.io/schemas/wn#hypernym";
    private static final String SKOS_EXACT_MATCH = "http://www.w3.org/2004/02/skos/core#exactMatch";

    private final OntologyService ontology;

    public SemanticTypeCompatibilityService(OntologyService ontology) {
        this.ontology = ontology;
    }

    public boolean isCompatible(String actualTypeIri, String expectedTypeIri) {
        if (!isIri(actualTypeIri) || !isIri(expectedTypeIri)) {
            return false;
        }
        if (actualTypeIri.equals(expectedTypeIri)) {
            return true;
        }
        if (ontology.isSubclassOf(actualTypeIri, expectedTypeIri)) {
            return true;
        }
        if (hasHypernymPath(actualTypeIri, expectedTypeIri)) {
            return true;
        }
        return isExactMatch(actualTypeIri, expectedTypeIri);
    }

    public boolean hasCompatibleType(Set<String> actualTypes, String expectedTypeIri) {
        if (actualTypes == null || actualTypes.isEmpty()) {
            return false;
        }
        if (!isIri(expectedTypeIri)) {
            return false;
        }
        for (String type : actualTypes) {
            if (!isIri(type)) {
                continue;
            }
            if (isCompatible(type, expectedTypeIri)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExactMatch(String left, String right) {
        if (!isIri(left) || !isIri(right)) {
            return false;
        }
        Set<String> forward = ontology.getObjectPropertyTargets(left, SKOS_EXACT_MATCH);
        if (forward.contains(right)) {
            return true;
        }
        Set<String> reverse = ontology.getObjectPropertyTargets(right, SKOS_EXACT_MATCH);
        return reverse.contains(left);
    }

    private boolean hasHypernymPath(String actual, String expected) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(actual);
        visited.add(actual);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> hypernyms = ontology.getObjectPropertyTargets(current, WORDNET_HYPERNYM);
            for (String hypernym : hypernyms) {
                if (!isIri(hypernym) || !visited.add(hypernym)) {
                    continue;
                }
                if (hypernym.equals(expected)) {
                    return true;
                }
                queue.add(hypernym);
            }
        }
        return false;
    }

    private boolean isIri(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }
}
