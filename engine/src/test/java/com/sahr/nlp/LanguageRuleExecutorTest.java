package com.sahr.nlp;

import com.sahr.core.QueryGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageRuleExecutorTest {
    private final LanguageGraphBuilder builder = new LanguageGraphBuilder(true);
    private final LanguageRuleExecutor executor = new LanguageRuleExecutor(true);

    @Test
    void interpretsWhPrepositionAsRelationQuery() {
        LanguageGraph graph = builder.build("Who is in the house");

        LanguageQueryCandidate candidate = executor.interpret(graph).stream().findFirst().orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("in", goal.predicate());
        assertEquals("house", goal.object());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsWhWithAsRelationQuery() {
        LanguageGraph graph = builder.build("Who is with the man");

        java.util.List<LanguageQueryCandidate> candidates = executor.interpret(graph);
        assertTrue(candidates.size() >= 2);
        LanguageQueryCandidate candidate = candidates.stream().findFirst().orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("with", goal.predicate());
        assertEquals("man", goal.object());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsTrailingPrepositionPattern() {
        LanguageGraph graph = builder.build("Who is the man with");

        LanguageQueryCandidate candidate = executor.interpret(graph).stream().findFirst().orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("with", goal.predicate());
        assertEquals("man", goal.subject());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsVerbObjectPattern() {
        LanguageGraph graph = builder.build("Who is wearing a hat");

        LanguageQueryCandidate candidate = executor.interpret(graph).stream().findFirst().orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("wear", goal.predicate());
        assertEquals("hat", goal.object());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsObjectVerbPattern() {
        LanguageGraph graph = builder.build("What is the man wearing");

        LanguageQueryCandidate candidate = executor.interpret(graph).stream().findFirst().orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("wear", goal.predicate());
        assertEquals("man", goal.subject());
        assertEquals("entity", goal.expectedType());
    }
}
