package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class OntologyDefinedHead extends BaseHead {
    private static final Logger logger = Logger.getLogger(OntologyDefinedHead.class.getName());
    private final List<OntologyHeadDefinition> definitions;
    private final Map<String, OntologyHeadExecutor> executors;
    private final Set<String> unknownExecutors = ConcurrentHashMap.newKeySet();

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
                warnUnknownExecutor(definition);
                continue;
            }
            List<ReasoningCandidate> produced = executor.execute(context, definition);
            if (!produced.isEmpty()) {
                candidates.addAll(overrideCandidates(definition, produced));
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
        register(map, new RuleInsertionExecutor());
        register(map, new RuleForwardChainExecutor());
        register(map, new IntentClassifierExecutor());
        register(map, new QueryProposerExecutor());
        return map;
    }

    private void register(Map<String, OntologyHeadExecutor> map, OntologyHeadExecutor executor) {
        map.put(executor.type(), executor);
    }

    private List<ReasoningCandidate> overrideCandidates(OntologyHeadDefinition definition,
                                                        List<ReasoningCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        double weight = definition.baseWeight();
        List<ReasoningCandidate> adjusted = new ArrayList<>(candidates.size());
        for (ReasoningCandidate candidate : candidates) {
            double headScore = clampScore(candidate.score() * weight);
            Map<String, Double> breakdown = new HashMap<>(candidate.scoreBreakdown());
            breakdown.put("head_base_weight", weight);
            adjusted.add(new ReasoningCandidate(
                    candidate.type(),
                    candidate.payload(),
                    headScore,
                    definition.name(),
                    candidate.evidence(),
                    breakdown,
                    candidate.inferenceDepth()
            ));
        }
        return adjusted;
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

    private void warnUnknownExecutor(OntologyHeadDefinition definition) {
        String type = definition.executorType();
        if (!unknownExecutors.add(type)) {
            return;
        }
        logger.warning(() -> "Unknown executorType '" + type + "' for OWL head '" + definition.name()
                + "'. Head will be skipped.");
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

    private static final class RuleInsertionExecutor implements OntologyHeadExecutor {
        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_RULE_INSERTION;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            return context.rule()
                    .map(rule -> {
                        Map<String, Double> breakdown = new HashMap<>();
                        breakdown.put("rule_confidence", rule.confidence());
                        double score = Math.min(1.0, rule.confidence());
                        return List.of(new ReasoningCandidate(
                                CandidateType.ASSERTION,
                                rule,
                                score,
                                definition.name(),
                                List.of(rule.toString()),
                                breakdown,
                                0
                        ));
                    })
                    .orElseGet(List::of);
        }
    }

    private static final class RuleForwardChainExecutor implements OntologyHeadExecutor {
        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_RULE_FORWARD_CHAIN;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            KnowledgeBase graph = context.graph();
            if (graph.getAllRules().isEmpty() || graph.getAllAssertions().isEmpty()) {
                return List.of();
            }
            List<ReasoningCandidate> candidates = new ArrayList<>();
            for (com.sahr.core.RuleAssertion rule : graph.getAllRules()) {
                RelationAssertion antecedent = rule.antecedent();
                boolean matched = graph.findByPredicate(antecedent.predicate()).stream()
                        .anyMatch(assertion -> assertion.subject().equals(antecedent.subject())
                                && assertion.object().equals(antecedent.object()));
                if (!matched) {
                    continue;
                }
                RelationAssertion consequent = rule.consequent();
                boolean alreadyPresent = graph.findByPredicate(consequent.predicate()).stream()
                        .anyMatch(assertion -> assertion.subject().equals(consequent.subject())
                                && assertion.object().equals(consequent.object()));
                if (alreadyPresent) {
                    continue;
                }
                Map<String, Double> breakdown = new HashMap<>();
                breakdown.put("rule_confidence", rule.confidence());
                breakdown.put("evidence_confidence", antecedent.confidence());
                double score = Math.min(1.0, (rule.confidence() + antecedent.confidence()) / 2.0);
                candidates.add(new ReasoningCandidate(
                        CandidateType.ASSERTION,
                        new RelationAssertion(consequent.subject(), consequent.predicate(), consequent.object(), score),
                        score,
                        definition.name(),
                        List.of(antecedent.toString(), rule.toString()),
                        breakdown,
                        1
                ));
            }
            return candidates;
        }
    }

    private static final class IntentClassifierExecutor implements OntologyHeadExecutor {
        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_INTENT_CLASSIFIER;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            var featuresOpt = context.inputFeatures();
            if (featuresOpt.isEmpty()) {
                return List.of();
            }
            String intentRaw = definition.executorParam("intent");
            if (intentRaw == null || intentRaw.isBlank()) {
                return List.of();
            }
            com.sahr.core.IntentType intent = parseIntent(intentRaw);
            if (intent == com.sahr.core.IntentType.UNKNOWN) {
                return List.of();
            }

            Set<String> required = splitParams(definition.executorParam("require"));
            Set<String> any = splitParams(definition.executorParam("any"));
            Set<String> forbid = splitParams(definition.executorParam("forbid"));
            Set<String> boost = splitParams(definition.executorParam("boost"));
            Set<String> penalty = splitParams(definition.executorParam("penalize"));

            com.sahr.nlp.InputFeatures features = featuresOpt.get();
            if (!required.isEmpty() && !hasAll(features, required)) {
                return List.of();
            }
            if (!any.isEmpty() && !hasAny(features, any)) {
                return List.of();
            }
            if (!forbid.isEmpty() && hasAny(features, forbid)) {
                return List.of();
            }

            double score = 0.6;
            score += boostCount(features, boost) * 0.08;
            score -= boostCount(features, penalty) * 0.08;
            score = clamp(score, 0.0, 1.0);

            List<String> evidence = new java.util.ArrayList<>();
            evidence.add("intent=" + intent.name().toLowerCase(java.util.Locale.ROOT));
            evidence.addAll(features.features());

            com.sahr.core.IntentDecision decision = new com.sahr.core.IntentDecision(intent, score, evidence);
            return List.of(new ReasoningCandidate(
                    CandidateType.INTENT,
                    decision,
                    score,
                    definition.name(),
                    evidence,
                    java.util.Map.of("intent_score", score),
                    0
            ));
        }

        private com.sahr.core.IntentType parseIntent(String raw) {
            String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (value) {
                case "question" -> com.sahr.core.IntentType.QUESTION;
                case "rule" -> com.sahr.core.IntentType.RULE;
                case "assertion" -> com.sahr.core.IntentType.ASSERTION;
                case "condition_query", "condition" -> com.sahr.core.IntentType.CONDITION_QUERY;
                default -> com.sahr.core.IntentType.UNKNOWN;
            };
        }

        private Set<String> splitParams(String raw) {
            if (raw == null || raw.isBlank()) {
                return Set.of();
            }
            String[] parts = raw.split(",");
            Set<String> values = new java.util.HashSet<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }

        private boolean hasAll(com.sahr.nlp.InputFeatures features, Set<String> required) {
            for (String feature : required) {
                if (!features.has(feature)) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasAny(com.sahr.nlp.InputFeatures features, Set<String> candidates) {
            for (String feature : candidates) {
                if (features.has(feature)) {
                    return true;
                }
            }
            return false;
        }

        private int boostCount(com.sahr.nlp.InputFeatures features, Set<String> candidates) {
            int count = 0;
            for (String feature : candidates) {
                if (features.has(feature)) {
                    count++;
                }
            }
            return count;
        }

        private double clamp(double value, double min, double max) {
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        }
    }

    private static final class QueryProposerExecutor implements OntologyHeadExecutor {
        private static final Set<String> STOPWORDS = Set.of(
                "the", "a", "an", "of", "to", "that", "this", "these", "those", "and", "or", "but",
                "in", "on", "at", "by", "for", "with", "from", "as", "is", "are", "was", "were",
                "be", "been", "being", "did", "do", "does", "can", "could", "should", "would",
                "will", "may", "might", "must", "shall", "most", "likely", "about", "after", "before",
                "during", "even", "though", "under", "what", "which", "why", "how", "who", "whom",
                "explain", "if", "then", "when"
        );
        private static final Set<String> AUX_STARTERS = Set.of(
                "is", "are", "was", "were", "do", "does", "did", "can", "could", "should", "would",
                "will", "may", "might", "must", "shall"
        );

        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_QUERY_PROPOSER;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            var featuresOpt = context.inputFeatures();
            if (featuresOpt.isEmpty()) {
                return List.of();
            }
            String mode = definition.executorParam("mode");
            if (mode == null || mode.isBlank()) {
                return List.of();
            }
            com.sahr.nlp.InputFeatures features = featuresOpt.get();
            String raw = features.raw();
            if (raw.isBlank()) {
                return List.of();
            }
            String lowered = raw.toLowerCase(java.util.Locale.ROOT);
            if ("condition".equalsIgnoreCase(mode)) {
                if (!features.has("has_if") || !features.has("has_wh")) {
                    return List.of();
                }
                String clause = clauseAfterComma(lowered);
                if (clause == null) {
                    return List.of();
                }
                QueryGoal proposed = proposeQueryFromText(clause);
                if (proposed == null) {
                    return List.of();
                }
                return List.of(buildCandidate(definition, proposed, "condition_clause"));
            }
            if ("question".equalsIgnoreCase(mode)) {
                boolean startsWithAux = startsWithAux(features.tokens());
                if (!features.has("has_wh") && !features.has("has_question_mark")
                        && !features.has("has_explain") && !startsWithAux) {
                    return List.of();
                }
                QueryGoal proposed = proposeQueryFromText(lowered);
                if (proposed == null) {
                    return List.of();
                }
                return List.of(buildCandidate(definition, proposed, "question_clause"));
            }
            return List.of();
        }

        private ReasoningCandidate buildCandidate(OntologyHeadDefinition definition,
                                                  QueryGoal query,
                                                  String evidenceTag) {
            double score = clamp(query == null ? 0.0 : definition.baseWeight(), 0.0, 1.0);
            List<String> evidence = List.of(evidenceTag, "query=" + query);
            return new ReasoningCandidate(
                    CandidateType.SUBGOAL,
                    query,
                    score,
                    definition.name(),
                    evidence,
                    java.util.Map.of("query_score", score),
                    0
            );
        }

        private QueryGoal proposeQueryFromText(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            return com.sahr.nlp.ShallowQueryExtractor.propose(text).orElse(null);
        }

        private String clauseAfterComma(String text) {
            if (text == null) {
                return null;
            }
            int idx = text.indexOf(',');
            if (idx < 0 || idx + 1 >= text.length()) {
                return null;
            }
            return text.substring(idx + 1).trim();
        }

        private boolean startsWithAux(java.util.List<String> tokens) {
            if (tokens == null || tokens.isEmpty()) {
                return false;
            }
            String first = tokens.get(0);
            return AUX_STARTERS.contains(first);
        }

        private double clamp(double value, double min, double max) {
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
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
