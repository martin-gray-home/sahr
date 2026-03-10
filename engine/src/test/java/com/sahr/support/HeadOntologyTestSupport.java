package com.sahr.support;

import com.sahr.core.HeadOntology;
import com.sahr.ontology.InMemoryOntologyService;

public final class HeadOntologyTestSupport {
    private static final String SAHR_NS = "https://sahr.ai/ontology/relations#";

    private HeadOntologyTestSupport() {
    }

    public static InMemoryOntologyService createOntology() {
        InMemoryOntologyService ontology = new InMemoryOntologyService();
        configure(ontology);
        return ontology;
    }

    public static InMemoryOntologyService configure(InMemoryOntologyService ontology) {
        if (ontology == null) {
            return null;
        }
        addLocationPredicate(ontology, "locatedIn");
        addLocationPredicate(ontology, "inside");
        addLocationPredicate(ontology, "in");
        addLocationTransferPredicate(ontology, "at");
        addSurfacePredicate(ontology, "on");
        addSurfacePredicate(ontology, "under");
        addLocationTransferPredicate(ontology, "above");
        addLocationTransferPredicate(ontology, "below");

        addColocationPredicate(ontology, "with");
        addColocationPredicate(ontology, "wear");
        addColocationPredicate(ontology, "wornBy");
        addColocationPredicate(ontology, "hold");
        addColocationPredicate(ontology, "carry");
        addColocationPredicate(ontology, "possess");
        addColocationPredicate(ontology, "have");
        addColocationPredicate(ontology, "opposite");
        addColocationPredicate(ontology, "partOf");
        addColocationPredicate(ontology, "colocation");

        addDependencyPredicate(ontology, "poweredBy");
        addDependencyPredicate(ontology, "chargedBy");

        addAttributePredicate(ontology, "hasAttribute");

        ontology.addInverseProperty(sahr("wear"), sahr("wornBy"));
        ontology.addInverseProperty(sahr("on"), sahr("under"));
        ontology.addInverseProperty(sahr("above"), sahr("below"));
        ontology.addSymmetricProperty(sahr("with"));
        ontology.addSymmetricProperty(sahr("opposite"));

        ontology.addInverseProperty("wear", "wornBy");
        ontology.addInverseProperty("on", "under");
        ontology.addInverseProperty("above", "below");
        ontology.addSymmetricProperty("with");
        ontology.addSymmetricProperty("opposite");

        return ontology;
    }

    private static void addLocationPredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.LOCATION_TRANSFER);
        addFamilyPredicate(ontology, predicate, HeadOntology.CONTAINMENT);
    }

    private static void addLocationTransferPredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.LOCATION_TRANSFER);
    }

    private static void addSurfacePredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.SURFACE_CONTACT);
    }

    private static void addColocationPredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.COLOCATION);
    }

    private static void addDependencyPredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.DEPENDENCY_CHAIN);
    }

    private static void addAttributePredicate(InMemoryOntologyService ontology, String predicate) {
        addFamilyPredicate(ontology, predicate, HeadOntology.ATTRIBUTE_RELATION);
    }

    private static void addFamilyPredicate(InMemoryOntologyService ontology, String predicate, String familyIri) {
        ontology.addSubproperty(predicate, familyIri);
        ontology.addSubproperty(sahr(predicate), familyIri);
    }

    private static String sahr(String predicate) {
        return SAHR_NS + predicate;
    }
}
