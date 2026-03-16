package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticNodeType;
import com.sahr.semantic.model.SemanticSourceReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticAlignmentCompilerTest {

    @Test
    void emitsUnresolvedAuditEntriesForSourceNodes() {
        SemanticSourceReference source = new SemanticSourceReference(
                "WordNet",
                "wn:12345",
                "person",
                0.8
        );

        SemanticNode node = new SemanticNode(
                "concept:person-like",
                "person",
                "person-like",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.STRONG,
                List.of(),
                List.of(source)
        );

        AlignmentInput input = new AlignmentInput(List.of(node), List.of(), List.of());
        SemanticAlignmentCompiler compiler = new NoopSemanticAlignmentCompiler();

        AlignmentOutput output = compiler.compile(input);

        assertEquals(1, output.report().entries().size());
        assertEquals(AlignmentConfidence.UNRESOLVED, output.report().entries().get(0).confidence());
        assertEquals(1, output.report().summary().countsByConfidence().get(AlignmentConfidence.UNRESOLVED));
    }

    @Test
    void alignsHeadOntologyRelationsToCanonicalFamilies() {
        SemanticSourceReference source = new SemanticSourceReference(
                "head",
                "https://sahr.ai/ontology/head#containment",
                "containment",
                1.0
        );

        SemanticNode node = new SemanticNode(
                "relation:containment",
                "containment",
                "unknown",
                SemanticNodeType.RELATION,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(source)
        );

        AlignmentInput input = new AlignmentInput(List.of(node), List.of(), List.of());
        SemanticAlignmentCompiler compiler = OntologyBackedSemanticAlignmentCompiler.loadDefault();

        AlignmentOutput output = compiler.compile(input);

        assertEquals("containment", output.canonicalNodes().get(0).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(0).confidence());
        assertEquals(1, output.report().entries().size());
        assertEquals(AlignmentConfidence.STRONG, output.report().entries().get(0).confidence());
    }

    @Test
    void alignsWordNetSeedsToCanonicalConceptFamilies() {
        SemanticSourceReference source = new SemanticSourceReference(
                "WordNet",
                "https://en-word.net/id/oewn-00007846-n",
                "person",
                0.9
        );

        SemanticNode node = new SemanticNode(
                "concept:person",
                "person",
                "unknown",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(source)
        );

        AlignmentInput input = new AlignmentInput(List.of(node), List.of(), List.of());
        SemanticAlignmentCompiler compiler = OntologyBackedSemanticAlignmentCompiler.loadDefault();

        AlignmentOutput output = compiler.compile(input);

        assertEquals("person-like", output.canonicalNodes().get(0).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(0).confidence());
    }

    @Test
    void alignsWordNetContainerToCanonicalConceptFamily() {
        SemanticSourceReference source = new SemanticSourceReference(
                "WordNet",
                "https://en-word.net/id/oewn-03099154-n",
                "container",
                0.9
        );

        SemanticNode node = new SemanticNode(
                "concept:container",
                "container",
                "unknown",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(source)
        );

        AlignmentInput input = new AlignmentInput(List.of(node), List.of(), List.of());
        SemanticAlignmentCompiler compiler = OntologyBackedSemanticAlignmentCompiler.loadDefault();

        AlignmentOutput output = compiler.compile(input);

        assertEquals("container-like", output.canonicalNodes().get(0).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(0).confidence());
    }

    @Test
    void alignsWordNetThingAndArtifactToObjectLike() {
        SemanticSourceReference thingSource = new SemanticSourceReference(
                "WordNet",
                "https://en-word.net/id/oewn-00002452-n",
                "thing",
                0.9
        );
        SemanticSourceReference artifactSource = new SemanticSourceReference(
                "WordNet",
                "https://en-word.net/id/oewn-00022119-n",
                "artifact",
                0.9
        );

        SemanticNode thingNode = new SemanticNode(
                "concept:thing",
                "thing",
                "unknown",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(thingSource)
        );
        SemanticNode artifactNode = new SemanticNode(
                "concept:artifact",
                "artifact",
                "unknown",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(artifactSource)
        );

        AlignmentInput input = new AlignmentInput(List.of(thingNode, artifactNode), List.of(), List.of());
        SemanticAlignmentCompiler compiler = OntologyBackedSemanticAlignmentCompiler.loadDefault();

        AlignmentOutput output = compiler.compile(input);

        assertEquals("object-like", output.canonicalNodes().get(0).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(0).confidence());
        assertEquals("object-like", output.canonicalNodes().get(1).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(1).confidence());
    }

    @Test
    void alignsSahrRelationsToCanonicalFamilies() {
        SemanticSourceReference source = new SemanticSourceReference(
                "sahr-relations",
                "https://sahr.ai/ontology/relations#near",
                "near",
                1.0
        );

        SemanticNode node = new SemanticNode(
                "relation:near",
                "near",
                "unknown",
                SemanticNodeType.RELATION,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of(source)
        );

        AlignmentInput input = new AlignmentInput(List.of(node), List.of(), List.of());
        SemanticAlignmentCompiler compiler = OntologyBackedSemanticAlignmentCompiler.loadDefault();

        AlignmentOutput output = compiler.compile(input);

        assertEquals("proximity", output.canonicalNodes().get(0).familyId());
        assertEquals(AlignmentConfidence.STRONG, output.canonicalNodes().get(0).confidence());
    }
}
