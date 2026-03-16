package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticSourceReference;

import java.util.ArrayList;
import java.util.List;

public final class NoopSemanticAlignmentCompiler implements SemanticAlignmentCompiler {

    @Override
    public AlignmentOutput compile(AlignmentInput input) {
        List<AlignmentAuditEntry> entries = new ArrayList<>();
        for (SemanticNode node : input.importedNodes()) {
            List<SemanticSourceReference> sources = node.sources();
            if (sources.isEmpty()) {
                continue;
            }
            for (SemanticSourceReference source : sources) {
                entries.add(new AlignmentAuditEntry(
                        source,
                        node.familyId(),
                        AlignmentConfidence.UNRESOLVED,
                        "no alignment rules configured"
                ));
            }
        }

        AlignmentSummary summary = AlignmentSummary.fromEntries(entries);
        AlignmentReport report = new AlignmentReport(entries, summary);

        return new AlignmentOutput(
                input.importedNodes(),
                input.triggers(),
                input.constraints(),
                input.propertySemantics(),
                report
        );
    }
}
