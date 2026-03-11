package com.sahr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineConfigTest {
    @Test
    void loadsOntologyAndHeadConfiguration() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/engine-test.properties");

        assertTrue(config.ontologyIds().contains("test"));
        assertTrue(config.ontologyIds().contains("head-ontology"));
        assertTrue(config.ontologyIds().contains("sahr-relations"));
        assertTrue(config.ontologyIds().contains("reasoning-heads"));
        assertEquals(2, config.headIds().size());
        assertEquals("graph-retrieval", config.headIds().get(0));
        assertEquals("ontology-defined", config.headIds().get(1));
        assertTrue(config.ontologyResources().containsKey("test"));
        assertEquals("ontology/test-ontology.owl", config.ontologyResources().get("test").get(0));
    }
}
