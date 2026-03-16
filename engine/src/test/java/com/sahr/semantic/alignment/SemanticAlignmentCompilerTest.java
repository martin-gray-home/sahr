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
}
