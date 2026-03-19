package com.sahr.scenarios;

import com.sahr.agent.CommandProcessor;
import com.sahr.agent.SahrAgent;
import com.sahr.core.AssertionLayer;
import com.sahr.core.AssertionRecord;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.RuleAtom;
import com.sahr.core.RuleFrame;
import com.sahr.core.RuleTerm;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiVariableRuleScenarioTest {
    @Test
    void composesTwoVariableRuleAcrossSentenceSegments() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "wear", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:room"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.constant("entity:room")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));

        String answer = agent.handle("What is the hat in?");

        assertEquals("entity:room", answer);
        AssertionRecord derived = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.INFERRED)
                .filter(record -> "entity:hat".equals(record.subject().value()))
                .filter(record -> "in".equals(record.predicate()))
                .filter(record -> "entity:room".equals(record.object().value()))
                .findFirst()
                .orElseThrow();
        assertEquals("binding x=entity:man, y=entity:hat", derived.provenance().derivationBinding());
        assertTrue(derived.provenance().supportingAssertionIds().size() >= 2);
    }

    @Test
    void explainShowsTwoVariableBindingForWinningPath() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "wear", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.constant("entity:room"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.constant("entity:room")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:room", agent.handle("What is the hat in?"));

        String explain = new CommandProcessor(agent).handle(":explain --depth 3 --verbose").output();

        assertTrue(explain.contains("binding x=entity:man, y=entity:hat"), explain);
        assertTrue(explain.contains("entity:man wear entity:hat"), explain);
        assertTrue(explain.contains("entity:man in entity:room"), explain);
        assertTrue(explain.contains("entity:hat in entity:room"), explain);
    }

    @Test
    void composesThreeVariableCarryRuleAcrossUserStatements() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "carry", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The woman is in the garden"));
        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));

        String answer = agent.handle("What is the bag in?");

        assertEquals("entity:garden", answer);
        AssertionRecord derived = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.INFERRED)
                .filter(record -> "entity:bag".equals(record.subject().value()))
                .filter(record -> "in".equals(record.predicate()))
                .filter(record -> "entity:garden".equals(record.object().value()))
                .findFirst()
                .orElseThrow();
        assertEquals("binding x=entity:woman, y=entity:bag, z=entity:garden",
                derived.provenance().derivationBinding());
    }

    @Test
    void firesThreeVariableCarryRuleIndependentlyOfAssertionOrder() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "carry", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));
        assertEquals("Assertion recorded.", agent.handle("The woman is in the garden"));

        assertEquals("entity:garden", agent.handle("What is the bag in?"));
    }

    @Test
    void composesLinkedThreeVariablePartOfContainmentRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "partOf", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The handle is part of the door"));
        assertEquals("Assertion recorded.", agent.handle("The door is in the house"));

        String answer = agent.handle("What is the handle in?");

        assertEquals("entity:house", answer);
        AssertionRecord derived = graph.getAssertionRecords().stream()
                .filter(record -> record.layer() == AssertionLayer.INFERRED)
                .filter(record -> "entity:handle".equals(record.subject().value()))
                .filter(record -> "in".equals(record.predicate()))
                .filter(record -> "entity:house".equals(record.object().value()))
                .findFirst()
                .orElseThrow();
        assertEquals("binding x=entity:handle, y=entity:door, z=entity:house",
                derived.provenance().derivationBinding());
        assertTrue(derived.provenance().supportingAssertionIds().size() >= 2);
    }

    @Test
    void explainShowsGenericLinkedBindingPathForPartOfContainmentRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "partOf", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The handle is part of the door"));
        assertEquals("Assertion recorded.", agent.handle("The door is in the house"));
        assertEquals("entity:house", agent.handle("What is the handle in?"));

        String explain = new CommandProcessor(agent).handle(":explain --depth 3 --verbose").output();

        assertTrue(explain.contains("binding x=entity:handle, y=entity:door, z=entity:house"), explain);
        assertTrue(explain.contains("entity:handle partOf entity:door"), explain);
        assertTrue(explain.contains("entity:door in entity:house"), explain);
        assertTrue(explain.contains("entity:handle in entity:house"), explain);
        assertTrue(explain.contains("supports=assertion-"), explain);
    }

    @Test
    void doesNotInferWhenOnlyOneAntecedentIsPresent() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "carry", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));

        assertEquals("No candidates produced.", agent.handle("What is the bag in?"));
    }

    @Test
    void maintainsSeparateBindingsForMultipleConcurrentMatches() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "carry", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "hold", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The woman is carrying a bag"));
        assertEquals("Assertion recorded.", agent.handle("The woman is in the garden"));
        assertEquals("Assertion recorded.", agent.handle("The man is holding a key"));
        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));

        assertEquals("entity:garden", agent.handle("What is the bag in?"));
        assertEquals("entity:room", agent.handle("What is the key in?"));
        assertTrue(graph.getAssertionRecords().stream().anyMatch(record ->
                record.layer() == AssertionLayer.INFERRED
                        && "entity:bag".equals(record.subject().value())
                        && "entity:garden".equals(record.object().value())));
        assertTrue(graph.getAssertionRecords().stream().anyMatch(record ->
                record.layer() == AssertionLayer.INFERRED
                        && "entity:key".equals(record.subject().value())
                        && "entity:room".equals(record.object().value())));
    }

    @Test
    void returnsSeveralDerivedAnswersWhenMultipleBindingsFitOneQueryTarget() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        graph.addRuleFrame(new RuleFrame(
                "x",
                java.util.List.of(
                        new RuleAtom(RuleTerm.variable("x"), "partOf", RuleTerm.variable("y")),
                        new RuleAtom(RuleTerm.variable("y"), "in", RuleTerm.variable("z"))
                ),
                new RuleAtom(RuleTerm.variable("x"), "in", RuleTerm.variable("z")),
                0.85
        ));
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The handle is part of the door"));
        assertEquals("Assertion recorded.", agent.handle("The door is in the house"));
        assertEquals("Assertion recorded.", agent.handle("The door is in the room"));

        String answer = agent.handle("What is the handle in?");

        assertTrue(isEntitySet(answer, "entity:house", "entity:room"), "Unexpected answer: " + answer);
    }

    private boolean isEntitySet(String actual, String... expected) {
        java.util.Set<String> actualSet = new java.util.LinkedHashSet<>();
        if (actual != null && !actual.isBlank()) {
            for (String part : actual.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    actualSet.add(trimmed);
                }
            }
        }
        return actualSet.equals(new java.util.LinkedHashSet<>(java.util.List.of(expected)));
    }
}
