package com.sahr.semantic.importer;

import com.sahr.config.EngineConfig;
import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwlAlignmentPipelineConfigTest {

    @Test
    void loadsOntologyBundlesFromConfigAndAligns() {
        EngineConfig config = EngineConfig.loadFromClasspath("sahr/semantic-import-test.properties");
        OwlAlignmentPipeline pipeline = OwlAlignmentPipeline.defaultPipeline();

        OwlAlignmentResult result = pipeline.runFromConfig(config, "TestConfig");
        AlignmentOutput output = result.alignment();

        assertEquals(1, result.report().classCount());
        assertEquals(1, result.report().objectPropertyCount());

        Optional<SemanticNode> personNode = output.canonicalNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals("https://en-word.net/id/oewn-00007846-n"))
                .findFirst();
        assertTrue(personNode.isPresent());
        assertEquals("person-like", personNode.get().familyId());
        assertEquals(AlignmentConfidence.STRONG, personNode.get().confidence());
    }
}
