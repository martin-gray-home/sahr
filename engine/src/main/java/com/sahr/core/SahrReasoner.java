package com.sahr.core;

import java.util.List;
import java.util.Optional;
public final class SahrReasoner {
    private final List<SymbolicAttentionHead> heads;
    private final SymbolicAttentionScorer attentionScorer;
    private final HeadExecutor headExecutor;
    private final CandidateSelector candidateSelector;

    public SahrReasoner(List<SymbolicAttentionHead> heads) {
        this(heads, new SymbolicAttentionScorer());
    }

    public SahrReasoner(List<SymbolicAttentionHead> heads, SymbolicAttentionScorer attentionScorer) {
        this.heads = List.copyOf(heads);
        this.attentionScorer = attentionScorer == null ? new SymbolicAttentionScorer() : attentionScorer;
        this.headExecutor = new HeadExecutor(this.heads, this.attentionScorer);
        this.candidateSelector = new CandidateSelector();
    }

    public List<ReasoningCandidate> reason(HeadContext context) {
        List<ReasoningCandidate> results = headExecutor.execute(context);
        return candidateSelector.rank(context, results);
    }

    public Optional<ReasoningCandidate> selectWinner(HeadContext context) {
        List<ReasoningCandidate> results = reason(context);
        return candidateSelector.selectWinner(context, results);
    }

    public List<SymbolicAttentionHead> heads() {
        return heads;
    }
}
