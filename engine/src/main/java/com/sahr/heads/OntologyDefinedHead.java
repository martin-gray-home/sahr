package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OntologyDefinedHead extends BaseHead {
    private final List<OntologyHeadDefinition> definitions;

    public OntologyDefinedHead(List<OntologyHeadDefinition> definitions) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }

    @Override
    public String getName() {
        return "ontology-defined";
    }

    @Override
    public List<ReasoningCandidate> evaluate(HeadContext context) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        KnowledgeBase graph = context.graph();
        List<RelationAssertion> assertions = graph.getAllAssertions();
        if (assertions.isEmpty()) {
            return List.of();
        }
        List<ReasoningCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OntologyHeadDefinition definition : definitions) {
            List<PatternBinding> bindings = matchPatterns(definition.patterns(), assertions);
            for (PatternBinding binding : bindings) {
                RelationAssertion inferred = buildAssertion(definition, binding);
                if (inferred == null) {
                    continue;
                }
                String key = inferred.subject().value() + "|" + inferred.predicate() + "|" + inferred.object().value();
                if (!seen.add(key)) {
                    continue;
                }
                double evidenceScore = averageConfidence(binding.evidence());
                double score = normalize(definition.baseWeight(), evidenceScore);
                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("base_weight", definition.baseWeight());
                breakdown.put("evidence_confidence", evidenceScore);
                breakdown.put("ontology_support", 1.0);
                candidates.add(new ReasoningCandidate(
                        CandidateType.ASSERTION,
                        inferred,
                        score,
                        getName(),
                        buildEvidence(binding.evidence()),
                        breakdown,
                        1
                ));
            }
        }
        return candidates;
    }

    private RelationAssertion buildAssertion(OntologyHeadDefinition definition, PatternBinding binding) {
        OntologyHeadDefinition.TriplePattern action = definition.action();
        String subject = resolveTerm(action.subject(), binding);
        String predicate = resolveTerm(action.predicate(), binding);
        String object = resolveTerm(action.object(), binding);
        if (subject == null || predicate == null || object == null) {
            return null;
        }
        return new RelationAssertion(new SymbolId(subject), predicate, new SymbolId(object), definition.baseWeight());
    }

    private String resolveTerm(OntologyHeadDefinition.Term term, PatternBinding binding) {
        if (term == null) {
            return null;
        }
        if (!term.isVariable()) {
            return term.value();
        }
        return binding.values.get(term.value());
    }

    private List<PatternBinding> matchPatterns(List<OntologyHeadDefinition.TriplePattern> patterns,
                                               List<RelationAssertion> assertions) {
        List<PatternBinding> results = new ArrayList<>();
        results.add(new PatternBinding());
        for (OntologyHeadDefinition.TriplePattern pattern : patterns) {
            List<PatternBinding> next = new ArrayList<>();
            for (PatternBinding binding : results) {
                for (RelationAssertion assertion : assertions) {
                    PatternBinding merged = matchPattern(pattern, assertion, binding);
                    if (merged != null) {
                        next.add(merged);
                    }
                }
            }
            results = next;
            if (results.isEmpty()) {
                break;
            }
        }
        return results;
    }

    private PatternBinding matchPattern(OntologyHeadDefinition.TriplePattern pattern,
                                        RelationAssertion assertion,
                                        PatternBinding binding) {
        PatternBinding updated = new PatternBinding(binding);
        if (!matchTerm(pattern.subject(), assertion.subject().value(), updated)) {
            return null;
        }
        if (!matchTerm(pattern.predicate(), assertion.predicate(), updated)) {
            return null;
        }
        if (!matchTerm(pattern.object(), assertion.object().value(), updated)) {
            return null;
        }
        updated.evidence.add(assertion);
        return updated;
    }

    private boolean matchTerm(OntologyHeadDefinition.Term term, String value, PatternBinding binding) {
        if (term == null || value == null) {
            return false;
        }
        if (!term.isVariable()) {
            return term.value().equals(value);
        }
        String name = term.value();
        String existing = binding.values.get(name);
        if (existing == null) {
            binding.values.put(name, value);
            return true;
        }
        return existing.equals(value);
    }

    private static final class PatternBinding {
        private final Map<String, String> values = new HashMap<>();
        private final List<RelationAssertion> evidence = new ArrayList<>();

        private PatternBinding() {
        }

        private PatternBinding(PatternBinding other) {
            this.values.putAll(other.values);
            this.evidence.addAll(other.evidence);
        }

        private List<RelationAssertion> evidence() {
            return evidence;
        }
    }
}
