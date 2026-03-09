package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolicAttentionHead;
import com.sahr.nlp.Statement;
import com.sahr.nlp.StatementBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AssertionInsertionHead implements SymbolicAttentionHead {
    @Override
    public String getName() {
        return "assertion-insertion";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        return context.statement()
                .map(this::candidateFor)
                .map(List::of)
                .orElseGet(List::of);
    }

    private ReasoningCandidate candidateFor(Statement statement) {
        Object payload = statement;
        if (!statement.additionalStatements().isEmpty()) {
            payload = StatementBatch.from(statement);
        }
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("query_match", 0.6);
        breakdown.put("ontology_support", 0.5);
        breakdown.put("graph_confidence", statement.confidence());

        double score = normalize(breakdown.values());

        return new ReasoningCandidate(
                CandidateType.ASSERTION,
                payload,
                score,
                getName(),
                List.of("statement:" + statement.predicate()),
                breakdown,
                0
        );
    }

    private double normalize(Iterable<Double> parts) {
        double total = 0.0;
        int count = 0;
        for (double part : parts) {
            total += part;
            count += 1;
        }
        return count == 0 ? 0.0 : Math.min(1.0, total / count);
    }
}
