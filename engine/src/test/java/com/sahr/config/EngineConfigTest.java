package com.sahr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineConfigTest {
    @Test
    void loadsOntologyAndHeadConfiguration() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/engine-test.properties");

        assertEquals(1, config.ontologyIds().size());
        assertEquals("test", config.ontologyIds().get(0));
        assertEquals(2, config.headIds().size());
        assertEquals("graph-retrieval", config.headIds().get(0));
        assertTrue(config.ontologyResources().containsKey("test"));
        assertEquals("ontology/test-ontology.owl", config.ontologyResources().get("test").get(0));
    }
}
