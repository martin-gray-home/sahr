package com.sahr.semantic.alignment;

import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.AlignmentRecord;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import com.sahr.semantic.model.LexicalTrigger;
import com.sahr.semantic.model.PropertySemantics;
import com.sahr.semantic.model.SelectionalConstraint;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticSourceReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OntologyBackedSemanticAlignmentCompiler implements SemanticAlignmentCompiler {
    private static final String DEFAULT_CANONICAL_RESOURCE = "ontology/sahr-canonical-families.ttl";
    private static final String DEFAULT_MAPPING_RESOURCE = "ontology/sahr-alignment-mappings.ttl";

    private final AlignmentMappingLoader mappings;

    public OntologyBackedSemanticAlignmentCompiler(AlignmentMappingLoader mappings) {
        this.mappings = Objects.requireNonNull(mappings, "mappings");
    }

    public static OntologyBackedSemanticAlignmentCompiler loadDefault() {
        CanonicalFamilyRegistry registry = CanonicalFamilyRegistry.loadFromClasspath(DEFAULT_CANONICAL_RESOURCE);
        AlignmentMappingLoader loader = AlignmentMappingLoader.loadFromClasspath(DEFAULT_MAPPING_RESOURCE, registry);
        return new OntologyBackedSemanticAlignmentCompiler(loader);
    }

    @Override
    public AlignmentOutput compile(AlignmentInput input) {
        List<SemanticNode> canonicalNodes = new ArrayList<>();
        List<LexicalTrigger> canonicalTriggers = new ArrayList<>(input.triggers());
        List<SelectionalConstraint> canonicalConstraints = new ArrayList<>(input.constraints());
        List<PropertySemantics> propertySemantics = new ArrayList<>(input.propertySemantics());
        List<AlignmentAuditEntry> entries = new ArrayList<>();

        for (SemanticNode node : input.importedNodes()) {
            AlignmentMatch bestMatch = bestMatchFor(node.sources());
            if (bestMatch != null) {
                AlignmentRule rule = bestMatch.rule;
                SemanticSourceReference source = bestMatch.source;
                AlignmentRecord alignment = new AlignmentRecord(
                        source,
                        rule.targetFamilyId(),
                        rule.confidence(),
                        defaultPolicy(rule.confidence())
                );
                SemanticNode alignedNode = new SemanticNode(
                        node.id(),
                        node.label(),
                        rule.targetFamilyId(),
                        node.type(),
                        rule.confidence(),
                        mergeAlignments(node, alignment),
                        node.sources()
                );
                canonicalNodes.add(alignedNode);
                entries.add(new AlignmentAuditEntry(
                        source,
                        rule.targetFamilyId(),
                        rule.confidence(),
                        rule.rationale()
                ));
            } else {
                canonicalNodes.add(node);
                for (SemanticSourceReference source : node.sources()) {
                    entries.add(new AlignmentAuditEntry(
                            source,
                            node.familyId(),
                            AlignmentConfidence.UNRESOLVED,
                            "no alignment mapping"
                    ));
                }
            }
        }

        AlignmentSummary summary = AlignmentSummary.fromEntries(entries);
        AlignmentReport report = new AlignmentReport(entries, summary);

        return new AlignmentOutput(canonicalNodes, canonicalTriggers, canonicalConstraints, propertySemantics, report);
    }

    private AlignmentMatch bestMatchFor(List<SemanticSourceReference> sources) {
        return sources.stream()
                .map(this::match)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(match -> match.rule.confidence(), confidenceRank()))
                .orElse(null);
    }

    private AlignmentMatch match(SemanticSourceReference source) {
        Optional<AlignmentRule> rule = mappings.ruleForSource(source.sourceId());
        return rule.map(alignmentRule -> new AlignmentMatch(source, alignmentRule)).orElse(null);
    }

    private static Comparator<AlignmentConfidence> confidenceRank() {
        return Comparator.comparingInt(confidence -> switch (confidence) {
            case EXACT -> 5;
            case STRONG -> 4;
            case MANUAL_OVERRIDE -> 3;
            case WEAK -> 2;
            case UNRESOLVED -> 1;
        });
    }

    private static InferencePolicy defaultPolicy(AlignmentConfidence confidence) {
        InferencePolicyStrength strength = switch (confidence) {
            case EXACT, STRONG, MANUAL_OVERRIDE -> InferencePolicyStrength.SOFT;
            case WEAK, UNRESOLVED -> InferencePolicyStrength.RANKING_HINT;
        };
        return new InferencePolicy(strength, true, "default alignment policy");
    }

    private static List<AlignmentRecord> mergeAlignments(SemanticNode node, AlignmentRecord alignment) {
        if (node.alignments().isEmpty()) {
            return List.of(alignment);
        }
        List<AlignmentRecord> merged = new ArrayList<>(node.alignments());
        merged.add(alignment);
        return List.copyOf(merged);
    }

    private static final class AlignmentMatch {
        private final SemanticSourceReference source;
        private final AlignmentRule rule;

        private AlignmentMatch(SemanticSourceReference source, AlignmentRule rule) {
            this.source = source;
            this.rule = rule;
        }
    }
}
