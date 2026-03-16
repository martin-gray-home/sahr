package com.sahr.semantic.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedSemanticModelTest {

    @Test
    void buildsSourceNeutralFamiliesAndNodes() {
        ConceptFamily conceptFamily = new ConceptFamily("person-like", "human or human-equivalent agents");
        RelationFamily relationFamily = new RelationFamily("containment", "in or contains");
        FrameFamily frameFamily = new FrameFamily("location-query", "where located");
        RoleType roleType = new RoleType("agent", "initiator");

        SelectionalConstraint constraint = new SelectionalConstraint(
                roleType,
                conceptFamily,
                ConstraintStrength.HARD,
                "agents must be person-like"
        );

        InferencePolicy policy = new InferencePolicy(InferencePolicyStrength.SOFT, true, "default soft expansion");

        SemanticSourceReference source = new SemanticSourceReference(
                "WordNet",
                "wn:12345",
                "person",
                0.9
        );

        AlignmentRecord alignment = new AlignmentRecord(
                source,
                conceptFamily.id(),
                AlignmentConfidence.STRONG,
                policy
        );

        SemanticNode node = new SemanticNode(
                "concept:person-like",
                "person",
                conceptFamily.id(),
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.STRONG,
                List.of(alignment),
                List.of(source)
        );

        LexicalTrigger trigger = new LexicalTrigger(
                "person",
                "person",
                conceptFamily.id(),
                TriggerFamilyType.CONCEPT,
                AlignmentConfidence.STRONG,
                List.of(source)
        );

        assertEquals("person-like", conceptFamily.id());
        assertEquals("containment", relationFamily.id());
        assertEquals("location-query", frameFamily.id());
        assertEquals("agent", constraint.role().id());
        assertEquals(AlignmentConfidence.STRONG, node.confidence());
        assertEquals(TriggerFamilyType.CONCEPT, trigger.familyType());
    }

    @Test
    void supportsNodesWithoutSourceSpecificDependency() {
        SemanticNode node = new SemanticNode(
                "concept:location-like",
                "location",
                "location-like",
                SemanticNodeType.CONCEPT,
                AlignmentConfidence.UNRESOLVED,
                List.of(),
                List.of()
        );

        assertTrue(node.sources().isEmpty());
        assertTrue(node.alignments().isEmpty());
        assertEquals(AlignmentConfidence.UNRESOLVED, node.confidence());
    }
}
