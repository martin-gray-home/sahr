package com.sahr.nlp;

import java.util.List;

public final class LanguageRuleCandidateProducer implements LanguageCandidateProducer {
    private final LanguageGraphBuilder graphBuilder;
    private final LanguageRuleExecutor ruleExecutor;

    public LanguageRuleCandidateProducer(boolean ontologyDriven) {
        this.graphBuilder = new LanguageGraphBuilder(ontologyDriven);
        this.ruleExecutor = new LanguageRuleExecutor(ontologyDriven);
    }

    @Override
    public List<LanguageQueryCandidate> produce(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return ruleExecutor.interpret(graphBuilder.build(input));
    }
}
