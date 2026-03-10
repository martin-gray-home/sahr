package com.sahr.ontology;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class VectorLexicalMapperTest {
    @Test
    void clustersEquivalentEntitiesAndSynonyms() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology();

        IRI spacecraft = IRI.create("https://example.org/spacecraft");
        IRI spaceship = IRI.create("https://example.org/spaceship");
        OWLClass spacecraftClass = factory.getOWLClass(spacecraft);
        OWLClass spaceshipClass = factory.getOWLClass(spaceship);
        manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(spacecraftClass, spaceshipClass));

        addLabel(factory, ontology, spacecraft, "spacecraft");
        addLabel(factory, ontology, spaceship, "spaceship");

        IRI exactSyn = IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
        addAnnotation(factory, ontology, spacecraft, exactSyn, "space vehicle");

        VectorLexicalMapper mapper = new VectorLexicalMapper(ontology, new HashingVectorizer(64), 0.0);
        VectorIndex index = extractEntityIndex(mapper);
        assertNotNull(index);

        float[] spacecraftVec = vectorForLabel(index, "spacecraft");
        float[] spaceshipVec = vectorForLabel(index, "spaceship");
        float[] spaceVehicleVec = vectorForLabel(index, "space_vehicle");

        assertNotNull(spacecraftVec);
        assertNotNull(spaceshipVec);
        assertNotNull(spaceVehicleVec);

        assertEquals(spacecraftVec.length, spaceshipVec.length);
        assertEquals(spacecraftVec.length, spaceVehicleVec.length);

        assertVectorsClose(spacecraftVec, spaceshipVec);
        assertVectorsClose(spacecraftVec, spaceVehicleVec);
    }

    @Test
    void keepsUnrelatedEntitiesInSeparateClusters() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology();

        IRI spacecraft = IRI.create("https://example.org/spacecraft");
        IRI vehicle = IRI.create("https://example.org/vehicle");
        addLabel(factory, ontology, spacecraft, "spacecraft");
        addLabel(factory, ontology, vehicle, "vehicle");

        VectorLexicalMapper mapper = new VectorLexicalMapper(ontology, new HashingVectorizer(64), 0.0);
        VectorIndex index = extractEntityIndex(mapper);
        assertNotNull(index);

        float[] spacecraftVec = vectorForLabel(index, "spacecraft");
        float[] vehicleVec = vectorForLabel(index, "vehicle");

        assertNotNull(spacecraftVec);
        assertNotNull(vehicleVec);
        assertNotSame(spacecraftVec, vehicleVec);
    }

    @Test
    void mapsEquivalentEntityLabelsToSameIri() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology();

        IRI spacecraft = IRI.create("https://example.org/spacecraft");
        IRI spaceship = IRI.create("https://example.org/spaceship");
        OWLClass spacecraftClass = factory.getOWLClass(spacecraft);
        OWLClass spaceshipClass = factory.getOWLClass(spaceship);
        manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(spacecraftClass, spaceshipClass));

        addLabel(factory, ontology, spacecraft, "spacecraft");
        addLabel(factory, ontology, spaceship, "spaceship");

        VectorLexicalMapper mapper = new VectorLexicalMapper(ontology, new HashingVectorizer(64), 0.0);

        String spacecraftIri = mapper.mapToken("spacecraft").orElse(null);
        String spaceshipIri = mapper.mapToken("spaceship").orElse(null);

        assertNotNull(spacecraftIri);
        assertNotNull(spaceshipIri);

        assertEquals(spacecraftIri, spaceshipIri);
    }

    @Test
    void mapsEquivalentPropertiesToSameIri() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology();

        IRI wear = IRI.create("https://example.org/wear");
        IRI don = IRI.create("https://example.org/don");
        OWLObjectProperty wearProp = factory.getOWLObjectProperty(wear);
        OWLObjectProperty donProp = factory.getOWLObjectProperty(don);
        manager.addAxiom(ontology, factory.getOWLEquivalentObjectPropertiesAxiom(wearProp, donProp));

        addLabel(factory, ontology, wear, "wear");
        addLabel(factory, ontology, don, "don");

        VectorLexicalMapper mapper = new VectorLexicalMapper(ontology, new HashingVectorizer(64), 0.0);

        String wearIri = mapper.mapPredicateToken("wear").orElse(null);
        String donIri = mapper.mapPredicateToken("don").orElse(null);

        assertNotNull(wearIri);
        assertNotNull(donIri);
        assertEquals(wearIri, donIri);
    }

    private void addLabel(OWLDataFactory factory, OWLOntology ontology, IRI subject, String label) {
        IRI rdfsLabel = IRI.create("http://www.w3.org/2000/01/rdf-schema#label");
        addAnnotation(factory, ontology, subject, rdfsLabel, label);
    }

    private void addAnnotation(OWLDataFactory factory, OWLOntology ontology, IRI subject, IRI property, String value) {
        OWLAnnotation annotation = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(property),
                factory.getOWLLiteral(value)
        );
        OWLAnnotationAssertionAxiom axiom = factory.getOWLAnnotationAssertionAxiom(subject, annotation);
        ontology.add(axiom);
    }

    private VectorIndex extractEntityIndex(VectorLexicalMapper mapper) throws Exception {
        Field field = VectorLexicalMapper.class.getDeclaredField("entityIndex");
        field.setAccessible(true);
        return (VectorIndex) field.get(mapper);
    }

    private float[] vectorForLabel(VectorIndex index, String label) {
        for (VectorIndex.Entry entry : index.entries()) {
            if (label.equals(entry.label())) {
                return entry.vector();
            }
        }
        return null;
    }

    private void assertVectorsClose(float[] left, float[] right) {
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        assertEquals(1.0, dot, 0.0001);
    }
}
