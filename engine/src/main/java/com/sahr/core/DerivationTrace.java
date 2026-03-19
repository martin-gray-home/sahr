package com.sahr.core;

import java.util.List;

public record DerivationTrace(
        String derivedAssertionId,
        String derivedAssertion,
        String rule,
        String binding,
        List<String> supportingAssertionIds
) {
    public DerivationTrace {
        derivedAssertionId = derivedAssertionId == null ? "" : derivedAssertionId;
        derivedAssertion = derivedAssertion == null ? "" : derivedAssertion;
        rule = rule == null ? "" : rule;
        binding = binding == null ? "" : binding;
        supportingAssertionIds = supportingAssertionIds == null ? List.of() : List.copyOf(supportingAssertionIds);
    }
}
