package com.sahr.config;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyRegistryTest {
    @Test
    void loadsOntologyResourcesFromConfig() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/engine-test.properties");
        OntologyContext context = OntologyRegistry.loadOntologyContext(config);
        OntologyService ontology = context.service();
        TermMapper mapper = context.termMapper();

        assertTrue(ontology.isSymmetricProperty("http://example.org/test#relatedTo"));
        assertTrue(ontology.isTransitiveProperty("http://example.org/test#locatedIn"));
        assertTrue(ontology.getInverseProperty("http://example.org/test#contains").isPresent());
        assertTrue(mapper.mapToken("doctor").isPresent());
    }
}