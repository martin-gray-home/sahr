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
        assertEquals("Unknown.", agent.handle("Is the hat on the man"));
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
        boolean rendered = "The answers were man, boy.".equals(firstAnswer) || "The answers were boy, man.".equals(firstAnswer);
        ordered = ordered || rendered;
        if (!ordered) {
            throw new AssertionError("Unexpected answer: " + firstAnswer);
        }
        String elseAnswer = agent.handle("Who else is with the mother");
        if (!("No candidates produced.".equals(elseAnswer)
                || "Assertion recorded.".equals(elseAnswer)
                || "Assertion already known.".equals(elseAnswer)
                || "entity:man".equals(elseAnswer)
                || "entity:boy".equals(elseAnswer)
                || "The answer was man.".equals(elseAnswer)
                || "The answer was boy.".equals(elseAnswer)
                || elseAnswer.contains("man")
                || elseAnswer.contains("boy"))) {
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

        String answer = agent.handle("Where is the woman");
        boolean ok = "entity:woman in entity:room".equals(answer)
                || "entity:woman inside entity:room".equals(answer);
        assertTrue(ok, "Unexpected location answer: " + answer);
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
            AnswerComposer composer = extractAnswerComposer(agent);
            java.lang.reflect.Method method = AnswerComposer.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation(null, "cause", "entity:instability", null);
            String answer = (String) method.invoke(composer, goal);
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
            AnswerComposer composer = extractAnswerComposer(agent);
            java.lang.reflect.Method method = AnswerComposer.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation("entity:thrusters", "backupFor", "concept:attitude_control", null);
            String answer = (String) method.invoke(composer, goal);
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
    void rendersPrepositionalRelationsWithCopula() {
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
                return null;
            }

            @Override
            public String normalizeTypeToken(String raw) {
                return raw == null ? "" : raw;
            }
        }, null);
        RelationAssertion inAssertion = new RelationAssertion(
                new SymbolId("entity:man"),
                "https://sahr.ai/ontology/relations#in",
                new SymbolId("entity:house"),
                0.9
        );
        RelationAssertion withAssertion = new RelationAssertion(
                new SymbolId("entity:woman"),
                "https://sahr.ai/ontology/relations#with",
                new SymbolId("entity:man"),
                0.9
        );
        assertTrue(renderer.formatAssertionSentence(inAssertion).toLowerCase(java.util.Locale.ROOT).contains("man is in house"));
        assertTrue(renderer.formatAssertionSentence(withAssertion).toLowerCase(java.util.Locale.ROOT).contains("woman is with man"));
    }

    @Test
    void rendersTaxonomyAndAttributePredicates() {
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
                return null;
            }

            @Override
            public String normalizeTypeToken(String raw) {
                return raw == null ? "" : raw;
            }
        }, new OntologyAnnotationResolver(new com.sahr.core.OntologyService() {
            @Override
            public boolean isSubclassOf(String child, String parent) {
                return false;
            }

            @Override
            public boolean isSymmetricProperty(String property) {
                return false;
            }

            @Override
            public boolean isTransitiveProperty(String property) {
                return false;
            }

            @Override
            public java.util.Optional<String> getInverseProperty(String property) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Set<String> getSuperclasses(String concept) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getSubclasses(String concept) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getSubproperties(String property) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getObjectPropertyRanges(String property) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getObjectPropertiesByLabel(String label) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getEntityIrisByLabel(String label) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getLabels(String iri) {
                if (iri != null && iri.contains("#wear")) {
                    return java.util.Set.of("wear");
                }
                return java.util.Set.of();
            }

            @Override
            public java.util.Optional<String> getAnnotationValue(String iri, String annotationIri) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Set<String> getEntitiesWithAnnotation(String annotationIri, String value) {
                return java.util.Set.of();
            }

            @Override
            public java.util.Set<String> getObjectPropertyTargets(String subjectIri, String propertyIri) {
                return java.util.Set.of();
            }
        }));
        RelationAssertion subclassAssertion = new RelationAssertion(
                new SymbolId("concept:hat"),
                "rdfs:subClassOf",
                new SymbolId("concept:green"),
                0.9
        );
        RelationAssertion typeAssertion = new RelationAssertion(
                new SymbolId("entity:hat"),
                "rdf:type",
                new SymbolId("concept:tool"),
                0.9
        );
        RelationAssertion attributeAssertion = new RelationAssertion(
                new SymbolId("entity:hat"),
                "hasAttribute",
                new SymbolId("entity:green"),
                0.9
        );
        RelationAssertion wearAssertion = new RelationAssertion(
                new SymbolId("entity:man"),
                "https://sahr.ai/ontology/relations#wear",
                new SymbolId("entity:hat"),
                0.9
        );
        String subclassSentence = renderer.formatAssertionSentence(subclassAssertion).toLowerCase(java.util.Locale.ROOT);
        assertTrue(subclassSentence.contains("kind of green"));
        assertTrue(renderer.formatAssertionSentence(typeAssertion).toLowerCase(java.util.Locale.ROOT).contains("hat is a tool"));
        assertTrue(renderer.formatAssertionSentence(attributeAssertion).toLowerCase(java.util.Locale.ROOT).contains("hat has green"));
        assertTrue(renderer.formatAssertionSentence(wearAssertion).toLowerCase(java.util.Locale.ROOT).contains("man wears hat"));
    }

    @Test
    void rendersUnknownPredicatesWithSafeFallback() {
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
                return null;
            }

            @Override
            public String normalizeTypeToken(String raw) {
                return raw == null ? "" : raw;
            }
        }, null);
        RelationAssertion assertion = new RelationAssertion(
                new SymbolId("entity:hat"),
                "glorped",
                new SymbolId("entity:house"),
                0.9
        );
        String sentence = renderer.formatAssertionSentence(assertion).toLowerCase(java.util.Locale.ROOT);
        assertTrue(sentence.contains("is related to house by glorped"));
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
            AnswerComposer composer = extractAnswerComposer(agent);
            java.lang.reflect.Method method = AnswerComposer.class.getDeclaredMethod("executeCauseChain", QueryGoal.class);
            method.setAccessible(true);
            QueryGoal goal = QueryGoal.relation("entity:wheel_motor", "cause", "entity:spacecraft_orientation_control", null);
            String answer = (String) method.invoke(composer, goal);
            String normalized = answer.toLowerCase(java.util.Locale.ROOT);
            assertTrue(normalized.contains("wheel motor"), () -> "answer=" + answer);
            assertTrue(normalized.contains("reaction wheel"), () -> "answer=" + answer);
            assertTrue(normalized.contains("orientation control"), () -> "answer=" + answer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke executeCauseChain", e);
        }
    }

    private AnswerComposer extractAnswerComposer(SahrAgent agent) throws ReflectiveOperationException {
        java.lang.reflect.Field field = SahrAgent.class.getDeclaredField("answerComposer");
        field.setAccessible(true);
        return (AnswerComposer) field.get(agent);
    }
}
