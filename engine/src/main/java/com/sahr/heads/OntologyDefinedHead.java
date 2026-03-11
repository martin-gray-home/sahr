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
    private final Map<String, OntologyHeadExecutor> executors;

    public OntologyDefinedHead(List<OntologyHeadDefinition> definitions,
                               Map<String, List<String>> predicateAliases) {
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
        this.executors = buildExecutors(predicateAliases);
    }

    public OntologyDefinedHead(List<OntologyHeadDefinition> definitions) {
        this(definitions, Map.of());
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
        List<ReasoningCandidate> candidates = new ArrayList<>();
        List<RelationAssertion> assertions = graph.getAllAssertions();
        Set<String> seen = new HashSet<>();
        for (OntologyHeadDefinition definition : definitions) {
            if (definition.isPatternMatch()) {
                if (assertions.isEmpty()) {
                    continue;
                }
                candidates.addAll(evaluatePattern(definition, assertions, seen));
                continue;
            }
            OntologyHeadExecutor executor = executors.get(definition.executorType());
            if (executor == null) {
                continue;
            }
            List<ReasoningCandidate> produced = executor.execute(context, definition);
            if (!produced.isEmpty()) {
                candidates.addAll(scaleCandidates(definition, produced));
            }
        }
        return candidates;
    }

    private List<ReasoningCandidate> evaluatePattern(OntologyHeadDefinition definition,
                                                     List<RelationAssertion> assertions,
                                                     Set<String> seen) {
        List<ReasoningCandidate> candidates = new ArrayList<>();
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
                    definition.name(),
                    buildEvidence(binding.evidence()),
                    breakdown,
                    1
            ));
        }
        return candidates;
    }

    private RelationAssertion buildAssertion(OntologyHeadDefinition definition, PatternBinding binding) {
        OntologyHeadDefinition.TriplePattern action = definition.action();
        if (action == null) {
            return null;
        }
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

    private Map<String, OntologyHeadExecutor> buildExecutors(Map<String, List<String>> predicateAliases) {
        Map<String, OntologyHeadExecutor> map = new HashMap<>();
        register(map, new AssertionInsertionExecutor());
        register(map, new RelationQueryExecutor(predicateAliases));
        register(map, new GraphRetrievalExecutor());
        register(map, new QueryAlignmentExecutor());
        register(map, new SubgoalExpansionExecutor());
        return map;
    }

    private void register(Map<String, OntologyHeadExecutor> map, OntologyHeadExecutor executor) {
        map.put(executor.type(), executor);
    }

    private List<ReasoningCandidate> scaleCandidates(OntologyHeadDefinition definition,
                                                     List<ReasoningCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        double weight = definition.baseWeight();
        if (Math.abs(weight - 1.0) < 0.0001) {
            return candidates;
        }
        List<ReasoningCandidate> scaled = new ArrayList<>(candidates.size());
        for (ReasoningCandidate candidate : candidates) {
            double headScore = clampScore(candidate.score() * weight);
            Map<String, Double> breakdown = new HashMap<>(candidate.scoreBreakdown());
            breakdown.put("head_base_weight", weight);
            scaled.add(new ReasoningCandidate(
                    candidate.type(),
                    candidate.payload(),
                    headScore,
                    candidate.producedBy(),
                    candidate.evidence(),
                    breakdown,
                    candidate.inferenceDepth()
            ));
        }
        return scaled;
    }

    private double clampScore(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
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

    private interface OntologyHeadExecutor {
        String type();

        List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition);
    }

    private static final class AssertionInsertionExecutor implements OntologyHeadExecutor {
        private final AssertionInsertionHead delegate = new AssertionInsertionHead();

        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_ASSERTION_INSERTION;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            return delegate.evaluate(context);
        }
    }

    private static final class RelationQueryExecutor implements OntologyHeadExecutor {
        private final RelationQueryHead delegate;

        private RelationQueryExecutor(Map<String, List<String>> predicateAliases) {
            this.delegate = new RelationQueryHead(predicateAliases);
        }

        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_RELATION_QUERY;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            return delegate.evaluate(context);
        }
    }

    private static final class GraphRetrievalExecutor implements OntologyHeadExecutor {
        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_GRAPH_RETRIEVAL;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            int maxDepth = parseInt(definition.executorParam("maxLocationDepth"), 6);
            return new GraphRetrievalHead(maxDepth).evaluate(context);
        }
    }

    private static final class QueryAlignmentExecutor implements OntologyHeadExecutor {
        private final QueryAlignmentHead delegate = new QueryAlignmentHead();

        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_QUERY_ALIGNMENT;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            return delegate.evaluate(context);
        }
    }

    private static final class SubgoalExpansionExecutor implements OntologyHeadExecutor {
        private final SubgoalExpansionHead delegate = new SubgoalExpansionHead();

        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_SUBGOAL_EXPANSION;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            return delegate.evaluate(context);
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
