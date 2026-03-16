package com.sahr.semantic.importer;

import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwlAlignmentPipelineResourceTest {

    @Test
    void loadsFromClasspathImportsAlignsAndReports() {
        OwlAlignmentPipeline pipeline = OwlAlignmentPipeline.defaultPipeline();
        OwlAlignmentResult result = pipeline.runFromClasspath(
                List.of("ontology/test-owl-ingest.ttl"),
                "TestResource"
        );

        assertEquals(1, result.report().classCount());
        assertEquals(1, result.report().objectPropertyCount());

        AlignmentOutput output = result.alignment();
        Optional<SemanticNode> personNode = output.canonicalNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals("https://en-word.net/id/oewn-00007846-n"))
                .findFirst();
        assertTrue(personNode.isPresent());
        assertEquals("person-like", personNode.get().familyId());
        assertEquals(AlignmentConfidence.STRONG, personNode.get().confidence());

        Optional<SemanticNode> nearNode = output.canonicalNodes().stream()
                .filter(node -> node.sources().get(0).sourceId().equals("https://sahr.ai/ontology/relations#near"))
                .findFirst();
        assertTrue(nearNode.isPresent());
        assertEquals("proximity", nearNode.get().familyId());
        assertEquals(AlignmentConfidence.STRONG, nearNode.get().confidence());
    }
}
