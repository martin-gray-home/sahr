package com.sahr.core;

import java.util.List;

public interface SymbolicAttentionHead {
    String getName();

    List<ReasoningCandidate> evaluate(HeadContext context);

    default String explain(HeadContext context) {
        return getName();
    }
}
