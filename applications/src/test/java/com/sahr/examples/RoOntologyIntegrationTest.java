package com.sahr.examples;

import com.sahr.config.EngineConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoOntologyIntegrationTest {
    @Test
    void loadsRoOntologyResource() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("ontology/ro.owl")) {
            assertNotNull(stream);
        } catch (Exception e) {
            throw new AssertionError("Failed to load ro.owl resource", e);
        }
    }

    @Test
    void registersRoOntologyInConfig() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/engine.properties");
        List<String> ids = config.ontologyIds();
        assertTrue(ids.contains("ro"));
    }
}
