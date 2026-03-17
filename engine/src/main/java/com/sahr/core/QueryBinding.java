package com.sahr.core;

import com.sahr.semantic.model.InferencePolicyStrength;

public record QueryBinding(SymbolId subject,
                           String predicate,
                           SymbolId object,
                           SymbolId answer,
                           PredicateMatchType matchType,
                           InferencePolicyStrength policyStrength,
                           double confidence,
                           String evidence) {
}
