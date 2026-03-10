package com.sahr.core;

import java.util.HashSet;
import java.util.Set;

public final class HeadOntology {
    private static final String NS = "https://sahr.ai/ontology/head#";

    public static final String COLOCATION = NS + "colocation";
    public static final String CONTAINMENT = NS + "containment";
    public static final String SURFACE_CONTACT = NS + "surfaceContact";
    public static final String LOCATION_TRANSFER = NS + "locationTransfer";
    public static final String DEPENDENCY_CHAIN = NS + "dependencyChain";
    public static final String ATTRIBUTE_RELATION = NS + "attributeRelation";

    private HeadOntology() {
    }

    public static Set<String> expandFamily(OntologyService ontology, String familyIri) {
        Set<String> expanded = new HashSet<>();
        if (familyIri == null || familyIri.isBlank()) {
            return expanded;
        }
        expanded.add(familyIri);
        expanded.addAll(ontology.getSubproperties(familyIri));
        return expanded;
    }

    public static Set<String> expandFamilyWithInverses(OntologyService ontology, String familyIri) {
        Set<String> expanded = expandFamily(ontology, familyIri);
        if (expanded.isEmpty()) {
            return expanded;
        }
        Set<String> snapshot = new HashSet<>(expanded);
        for (String predicate : snapshot) {
            ontology.getInverseProperty(predicate).ifPresent(inverse -> {
                expanded.add(inverse);
                expanded.addAll(ontology.getSubproperties(inverse));
            });
        }
        return expanded;
    }
}
