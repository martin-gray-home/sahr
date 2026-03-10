package com.sahr.agent;

import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SymbolId;

import java.util.List;

public record WorkingMemorySnapshot(
        List<SymbolId> activeEntities,
        List<RelationAssertion> recentAssertions,
        List<QueryGoal> goalStack
) {
}
