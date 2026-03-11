package com.sahr.core;

import java.util.List;

public record IntentDecision(IntentType type, double score, List<String> evidence) {
    public IntentDecision {
        type = type == null ? IntentType.UNKNOWN : type;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
