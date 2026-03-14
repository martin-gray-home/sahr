package com.sahr.nlp;

import com.sahr.core.QueryGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageRuleExecutorTest {
    private final LanguageGraphBuilder builder = new LanguageGraphBuilder(true);
    private final LanguageRuleExecutor executor = new LanguageRuleExecutor(true);

    @Test
    void interpretsWhPrepositionAsRelationQuery() {
        LanguageGraph graph = builder.build("Who is in the house");

        LanguageQueryCandidate candidate = executor.interpret(graph).orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("in", goal.predicate());
        assertEquals("house", goal.object());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsWhWithAsRelationQuery() {
        LanguageGraph graph = builder.build("Who is with the man");

        LanguageQueryCandidate candidate = executor.interpret(graph).orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("with", goal.predicate());
        assertEquals("man", goal.object());
        assertEquals("person", goal.expectedType());
    }

    @Test
    void interpretsTrailingPrepositionPattern() {
        LanguageGraph graph = builder.build("Who is the man with");

        LanguageQueryCandidate candidate = executor.interpret(graph).orElseThrow();
        QueryGoal goal = candidate.queryGoal();

        assertEquals(QueryGoal.Type.RELATION, goal.type());
        assertEquals("with", goal.predicate());
        assertEquals("man", goal.subject());
        assertEquals("person", goal.expectedType());
    }
}
