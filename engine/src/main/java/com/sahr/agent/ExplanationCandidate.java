package com.sahr.agent;

import com.sahr.core.SymbolId;

import java.util.List;

final class ExplanationCandidate {
    private final List<String> sentences;
    private final List<SymbolId> precursorSignals;
    private final List<SymbolId> componentFailures;
    private final List<SymbolId> subsystemFailures;
    private final List<SymbolId> capabilityLosses;
    private final List<SymbolId> outcomes;
    private final List<SymbolId> recoveryAgents;
    private final List<SymbolId> evidenceNodes;

    ExplanationCandidate(List<String> sentences,
                         List<SymbolId> precursorSignals,
                         List<SymbolId> componentFailures,
                         List<SymbolId> subsystemFailures,
                         List<SymbolId> capabilityLosses,
                         List<SymbolId> outcomes,
                         List<SymbolId> recoveryAgents,
                         List<SymbolId> evidenceNodes) {
        this.sentences = sentences;
        this.precursorSignals = precursorSignals;
        this.componentFailures = componentFailures;
        this.subsystemFailures = subsystemFailures;
        this.capabilityLosses = capabilityLosses;
        this.outcomes = outcomes;
        this.recoveryAgents = recoveryAgents;
        this.evidenceNodes = evidenceNodes;
    }

    List<String> sentences() {
        return sentences;
    }

    List<SymbolId> precursorSignals() {
        return precursorSignals;
    }

    List<SymbolId> componentFailures() {
        return componentFailures;
    }

    List<SymbolId> subsystemFailures() {
        return subsystemFailures;
    }

    List<SymbolId> capabilityLosses() {
        return capabilityLosses;
    }

    List<SymbolId> outcomes() {
        return outcomes;
    }

    List<SymbolId> recoveryAgents() {
        return recoveryAgents;
    }

    List<SymbolId> evidenceNodes() {
        return evidenceNodes;
    }
}
