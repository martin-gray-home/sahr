package com.sahr.config;

import com.sahr.core.SymbolicAttentionHead;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadRegistryTest {
    @Test
    void buildsHeadsInConfiguredOrder() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/engine-test.properties");
        List<SymbolicAttentionHead> heads = HeadRegistry.buildHeads(config);

        assertEquals(2, heads.size());
        assertEquals("graph-retrieval", heads.get(0).getName());
        assertEquals("ontology-reasoning", heads.get(1).getName());
    }

    @Test
    void throwsOnUnknownHeadId() {
        EngineConfig config = EngineConfig.fromValues(
                List.of("test"),
                java.util.Map.of("test", List.of("ontology/test-ontology.owl")),
                List.of("unknown-head")
        );

        assertThrows(IllegalArgumentException.class, () -> HeadRegistry.buildHeads(config));
    }
}
