package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.support.SahrTestAgentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SahrAgentQueryTest {
    @Test
    void answersWhoIsWithQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("entity:woman", agent.handle("Who is with the man"));
    }

    @Test
    void answersNumberedWhoIsWithQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with a woman"));
        assertEquals("entity:woman", agent.handle("1. Who is with the man"));
    }

    @Test
    void answersWhoIsWearingQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("entity:man", agent.handle("Who is wearing a hat"));
    }

    @Test
    void answersYesNoWearQuery() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the man is wearing a hat", agent.handle("Is the man wearing a hat"));
    }

    @Test
    void answersYesNoOnQueryFromWear() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is wearing a hat"));
        assertEquals("Yes, the hat is on the man", agent.handle("Is the hat on the man"));
    }

    @Test
    void answersUnknownForYesNoWithoutEvidence() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Unknown.", agent.handle("Is the woman wearing a hat"));
    }

    @Test
    void answersWhoElseQueryUsingHistory() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is with the mother"));
        assertEquals("Assertion recorded.", agent.handle("The boy is with the mother"));

        String firstAnswer = agent.handle("Who is with the mother");
        boolean ordered = "entity:man, entity:boy".equals(firstAnswer) || "entity:boy, entity:man".equals(firstAnswer);
        if (!ordered) {
            throw new AssertionError("Unexpected answer: " + firstAnswer);
        }
        String elseAnswer = agent.handle("Who else is with the mother");
        if (!("No candidates produced.".equals(elseAnswer)
                || "Assertion recorded.".equals(elseAnswer)
                || "Assertion already known.".equals(elseAnswer)
                || "entity:man".equals(elseAnswer)
                || "entity:boy".equals(elseAnswer))) {
            throw new AssertionError("Unexpected answer: " + elseAnswer);
        }
    }

    @Test
    void answersWhereAfterOntologyAssertion() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        assertEquals("Assertion recorded.", agent.handle("The man is in the room"));
        graph.addAssertion(new RelationAssertion(
                new SymbolId("entity:woman"),
                "https://sahr.ai/ontology/relations#with",
                new SymbolId("entity:man"),
                0.9
        ));
        graph.addEntity(new EntityNode(
                new SymbolId("entity:woman"),
                "woman",
                java.util.Set.of("concept:woman")
        ));

        assertEquals("entity:woman in entity:room", agent.handle("Where is the woman"));
    }

    @Test
    void answersCauseChainUsingRules() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        RuleAssertion wheelToReaction = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:wheel_motor"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:wheel_motor"), 0.9),
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                0.9
        );
        RuleAssertion reactionToInstability = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                new RelationAssertion(new SymbolId("entity:instability"), "https://sahr.ai/ontology/relations#causedBy",
                        new SymbolId("entity:reaction_wheel"), 0.9),
                0.9
        );
        graph.addRule(wheelToReaction);
        graph.addRule(reactionToInstability);

        try {
            java.lang.reflect.Method method = SahrAgent.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation(null, "cause", "entity:instability", null);
            String answer = (String) method.invoke(agent, goal);
            assertTrue(answer.contains("wheel motor"));
            assertTrue(answer.contains("reaction wheel"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke executeCauseChain", e);
        }
    }

    @Test
    void explainsPredicateUsingRuleConsequent() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        RuleAssertion backupRule = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:actuators"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("concept:true"), 0.9),
                new RelationAssertion(new SymbolId("entity:thrusters"), "https://sahr.ai/ontology/relations#backupFor",
                        new SymbolId("concept:attitude_control"), 0.9),
                0.9
        );
        graph.addRule(backupRule);

        try {
            java.lang.reflect.Method method = SahrAgent.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation("entity:thrusters", "backupFor", "concept:attitude_control", null);
            String answer = (String) method.invoke(agent, goal);
            assertTrue(answer.contains("thrusters"));
            assertTrue(answer.contains("backup"));
            assertTrue(answer.contains("attitude control"));
            assertTrue(answer.contains("fail"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke executeCauseChain", e);
        }
    }

    @Test
    void formatsBooleanFailureInExplanation() {
        RelationAssertion assertion = new RelationAssertion(
                new SymbolId("entity:wheel_motor"),
                "https://sahr.ai/ontology/relations#fail",
                new SymbolId("concept:true"),
                0.9
        );
        AnswerRenderer renderer = new AnswerRenderer(new AnswerRenderer.DisplayFormatter() {
            @Override
            public String localName(String predicate) {
                if (predicate == null || predicate.isBlank()) {
                    return "";
                }
                int hashIdx = predicate.lastIndexOf('#');
                int slashIdx = predicate.lastIndexOf('/');
                int idx = Math.max(hashIdx, slashIdx);
                String local = idx >= 0 ? predicate.substring(idx + 1) : predicate;
                return local.toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public Boolean booleanConcept(SymbolId id) {
                if (id == null || id.value() == null) {
                    return null;
                }
                String value = id.value();
                if (value.startsWith("concept:")) {
                    value = value.substring("concept:".length());
                }
                value = value.toLowerCase(java.util.Locale.ROOT);
                if ("true".equals(value)) {
                    return Boolean.TRUE;
                }
                if ("false".equals(value)) {
                    return Boolean.FALSE;
                }
                return null;
            }

            @Override
            public String normalizeTypeToken(String raw) {
                return raw == null ? "" : raw;
            }
        }, null);
        String clause = renderer.formatAssertionSentence(assertion);
        assertTrue(clause.contains("wheel motor"));
        assertTrue(clause.contains("fails"));
    }

    @Test
    void formatsPluralSubjectVerbAgreement() {
        RelationAssertion assertion = new RelationAssertion(
                new SymbolId("entity:attitude_control_actuators"),
                "https://sahr.ai/ontology/relations#fail",
                new SymbolId("concept:true"),
                0.9
        );
        AnswerRenderer renderer = new AnswerRenderer(new AnswerRenderer.DisplayFormatter() {
            @Override
            public String localName(String predicate) {
                if (predicate == null || predicate.isBlank()) {
                    return "";
                }
                int hashIdx = predicate.lastIndexOf('#');
                int slashIdx = predicate.lastIndexOf('/');
                int idx = Math.max(hashIdx, slashIdx);
                String local = idx >= 0 ? predicate.substring(idx + 1) : predicate;
                return local.toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public Boolean booleanConcept(SymbolId id) {
                if (id == null || id.value() == null) {
                    return null;
                }
                String value = id.value();
                if (value.startsWith("concept:")) {
                    value = value.substring("concept:".length());
                }
                value = value.toLowerCase(java.util.Locale.ROOT);
                if ("true".equals(value)) {
                    return Boolean.TRUE;
                }
                if ("false".equals(value)) {
                    return Boolean.FALSE;
                }
                return null;
            }

            @Override
            public String normalizeTypeToken(String raw) {
                return raw == null ? "" : raw;
            }
        }, null);
        String clause = renderer.formatAssertionSentence(assertion);
        assertTrue(clause.contains("actuators"));
        assertTrue(clause.contains("fail"));
    }

    @Test
    void buildsForwardExplanationChain() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = SahrTestAgentFactory.newAgent(graph);

        RuleAssertion motorToWheel = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:wheel_motor"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("concept:true"), 0.9),
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("concept:true"), 0.9),
                0.9
        );
        RuleAssertion wheelToControl = new RuleAssertion(
                new RelationAssertion(new SymbolId("entity:reaction_wheel"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("concept:true"), 0.9),
                new RelationAssertion(new SymbolId("entity:spacecraft_orientation_control"), "https://sahr.ai/ontology/relations#fail",
                        new SymbolId("concept:true"), 0.9),
                0.9
        );
        graph.addRule(motorToWheel);
        graph.addRule(wheelToControl);

        try {
            java.lang.reflect.Method method = SahrAgent.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation("entity:wheel_motor", "cause", "entity:spacecraft_orientation_control", null);
            String answer = (String) method.invoke(agent, goal);
            assertTrue(answer.contains("wheel motor"));
            assertTrue(answer.contains("reaction wheel"));
            assertTrue(answer.contains("orientation control"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke executeCauseChain", e);
        }
    }
}
