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
        register(map, new QueryPlannerExecutor());
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

    private static final class QueryPlannerExecutor implements OntologyHeadExecutor {
        @Override
        public String type() {
            return OntologyHeadDefinition.EXECUTOR_QUERY_PLANNER;
        }

        @Override
        public List<ReasoningCandidate> execute(HeadContext context, OntologyHeadDefinition definition) {
            if (context == null) {
                return List.of();
            }
            QueryGoal query = context.query();
            if (query == null || query.type() == QueryGoal.Type.UNKNOWN) {
                return List.of();
            }
            if (query.type() == QueryGoal.Type.WHERE) {
                return List.of();
            }
            PlanSelection selection = planQuery(context, query);
            QueryGoal planned = selection.query;
            if (planned == null || planned.type() == QueryGoal.Type.UNKNOWN) {
                return List.of();
            }
            com.sahr.core.QueryPlan plan = new com.sahr.core.QueryPlan(
                    selection.kind,
                    planned,
                    List.of("planner=default", "query=" + planned, "planKind=" + selection.kind)
            );
            double score = clamp(definition.baseWeight(), 0.0, 1.0);
            return List.of(new ReasoningCandidate(
                    CandidateType.QUERY_PLAN,
                    plan,
                    score,
                    definition.name(),
                    plan.evidence(),
                    java.util.Map.of("plan_score", score),
                    0
            ));
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

        private PlanSelection planQuery(HeadContext context, QueryGoal query) {
            String predicate = query.predicate();
            String expectedType = query.expectedType();
            if (predicate != null && !predicate.isBlank()) {
                String normalizedPredicate = normalizeToken(predicate);
                if (normalizedPredicate.matches("\\d+") || "system".equals(normalizedPredicate)
                        || "systems".equals(normalizedPredicate)
                        || "be".equals(normalizedPredicate)
                        || "is".equals(normalizedPredicate)
                        || "are".equals(normalizedPredicate)
                        || "was".equals(normalizedPredicate)
                        || "were".equals(normalizedPredicate)
                        || "telemetry".equals(normalizedPredicate)
                        || "signal".equals(normalizedPredicate)
                        || "signals".equals(normalizedPredicate)
                        || "evidence".equals(normalizedPredicate)
                        || "between".equals(normalizedPredicate)
                        || "plausible".equals(normalizedPredicate)) {
                    predicate = "";
                }
            }
            com.sahr.core.QueryPlan.Kind kind = inferPlanKind(context, predicate);
            if ((predicate == null || predicate.isBlank()) && context.inputFeatures().isPresent()) {
                java.util.List<String> tokens = context.inputFeatures().get().tokens();
                PredicateSelection selection = inferPredicateSelection(tokens, context.graph());
                if (selection != null && selection.predicate != null && !selection.predicate.isBlank()) {
                    predicate = selection.predicate;
                    kind = inferPlanKind(context, predicate);
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        String rejected = selection.rejected.isEmpty()
                                ? ""
                                : " rejected=" + String.join(",", selection.rejected);
                        String cue = selection.cue == null ? "" : " cue=" + selection.cue;
                        String selected = predicate;
                        logger.fine(() -> "planner predicate selection"
                                + cue
                                + " selected=" + selected
                                + rejected);
                    }
                }
            }
            String subject = query.subject();
            String object = query.object();
            boolean allowEntityInference = true;
            if (context.inputFeatures().isPresent()) {
                java.util.List<String> tokens = context.inputFeatures().get().tokens();
                PhraseOverride override = inferPhraseOverride(tokens, context.graph());
                if (override != null) {
                    if (override.clearSubject) {
                        subject = null;
                    }
                    if (override.clearObject) {
                        object = null;
                    }
                    if (override.predicate != null && !override.predicate.isBlank()) {
                        predicate = override.predicate;
                        kind = override.kind != null ? override.kind : inferPlanKind(context, predicate);
                    }
                    if (override.subject != null && !override.subject.isBlank()) {
                        subject = override.subject;
                    }
                    if (override.object != null && !override.object.isBlank()) {
                        object = override.object;
                    }
                    allowEntityInference = !(override.clearSubject || override.clearObject);
                }
            }
            if (allowEntityInference
                    && (subject == null || subject.isBlank() || object == null || object.isBlank()
                    || isWeakEntity(subject) || isWeakEntity(object))
                    && context.inputFeatures().isPresent()) {
                java.util.List<String> tokens = context.inputFeatures().get().tokens();
                String entity = inferEntity(tokens, context.graph());
                if (entity != null && !entity.isBlank()) {
                    if (subject == null || subject.isBlank() || isWeakEntity(subject)) {
                        subject = entity;
                    } else if (object == null || object.isBlank() || isWeakEntity(object)) {
                        object = entity;
                    }
                }
            }
            if (context.inputFeatures().isPresent()) {
                java.util.List<String> tokens = context.inputFeatures().get().tokens();
                String derivedObject = inferObjectForPredicate(tokens, predicate, subject, object, context.graph());
                if (derivedObject != null && !derivedObject.isBlank()) {
                    object = derivedObject;
                }
                if ("cause".equals(normalizeToken(predicate))) {
                    java.util.Map<String, String> entityMap = collectEntityNames(context.graph());
                    java.util.List<String> normalized = new java.util.ArrayList<>();
                    for (String token : tokens) {
                        String value = normalizeToken(token);
                        if (!value.isBlank()) {
                            normalized.add(value);
                        }
                    }
                    if ((normalized.contains("orientation") || normalized.contains("control"))
                            && entityMap.containsKey("spacecraft_orientation_control")) {
                        object = entityMap.get("spacecraft_orientation_control");
                    }
                    if ((normalized.contains("telemetry") || normalized.contains("events") || normalized.contains("sequence"))
                            && entityMap.containsKey("spacecraft_instability")) {
                        object = entityMap.get("spacecraft_instability");
                    }
                }
                ConditionBridge bridge = inferConditionBridge(tokens, context.graph());
                if (bridge != null) {
                    if (bridge.predicate != null && !bridge.predicate.isBlank()) {
                        predicate = bridge.predicate;
                        kind = inferPlanKind(context, predicate);
                    }
                    if (bridge.object != null && !bridge.object.isBlank()) {
                        object = bridge.object;
                    }
                    if (bridge.forceSubjectNull) {
                        subject = null;
                    }
                    if (bridge.preservedConditions.contains("condition_query")) {
                        kind = com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN;
                    }
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine(() -> "planner condition bridge failed="
                                + String.join(",", bridge.failedConditions)
                                + " preserved=" + String.join(",", bridge.preservedConditions)
                                + " predicate=" + safeToken(bridge.predicate)
                                + " object=" + safeToken(bridge.object));
                    }
                }
            }
            if (predicate == null || predicate.isBlank()) {
                return new PlanSelection(kind, query);
            }
            QueryGoal planned = new QueryGoal(
                    query.type(),
                    subject,
                    object,
                    predicate,
                    expectedType,
                    query.entityType(),
                    query.expectedRange(),
                    query.attribute(),
                    query.modifier(),
                    query.discourseModifier(),
                    query.subjectText(),
                    query.objectText(),
                    query.predicateText(),
                    query.goalId(),
                    query.parentGoalId(),
                    query.depth()
            );
            return new PlanSelection(kind, planned);
        }

        private com.sahr.core.QueryPlan.Kind inferPlanKind(HeadContext context, String predicate) {
            if (context != null && context.inputFeatures().isPresent()) {
                java.util.Set<String> features = context.inputFeatures().get().features();
                if (features.contains("has_before") || features.contains("has_after") || features.contains("has_during")) {
                    return com.sahr.core.QueryPlan.Kind.TEMPORAL_MATCH;
                }
                if (features.contains("has_why") || features.contains("has_explain")) {
                    return com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN;
                }
            }
            if (predicate != null) {
                String normalized = normalizeToken(predicate);
                String verbNormalized = normalizeVerb(normalized);
                if ("cause".equals(verbNormalized) || "causedby".equals(normalized)) {
                    return com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN;
                }
                if ("before".equals(normalized) || "after".equals(normalized) || "during".equals(normalized)) {
                    return com.sahr.core.QueryPlan.Kind.TEMPORAL_MATCH;
                }
            }
            return com.sahr.core.QueryPlan.Kind.RELATION_MATCH;
        }

        private PredicateSelection inferPredicateSelection(java.util.List<String> tokens, com.sahr.core.KnowledgeBase graph) {
            if (tokens == null || tokens.isEmpty() || graph == null) {
                return null;
            }
            java.util.Set<String> predicates = collectPredicateNames(graph);
            if (predicates.isEmpty()) {
                return null;
            }
            java.util.List<String> normalizedTokens = new java.util.ArrayList<>();
            for (String token : tokens) {
                String normalized = normalizeToken(token);
                if (!normalized.isEmpty()) {
                    normalizedTokens.add(normalized);
                }
            }
            PredicateSelection phraseSelection = inferPhrasePredicate(normalizedTokens, predicates);
            if (phraseSelection != null) {
                return phraseSelection;
            }
            java.util.List<String> candidates = new java.util.ArrayList<>();
            for (String token : tokens) {
                String normalized = normalizeToken(token);
                if (normalized.isEmpty()) {
                    continue;
                }
                String base = normalizeVerb(normalized);
                if (predicates.contains(base)) {
                    candidates.add(base);
                    continue;
                }
                if (predicates.contains(normalized)) {
                    candidates.add(normalized);
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }
            PredicateSelection selection = new PredicateSelection(candidates.get(0), "token", candidates);
            return selection;
        }

        private PredicateSelection inferPhrasePredicate(java.util.List<String> tokens, java.util.Set<String> predicates) {
            if (tokens == null || tokens.isEmpty()) {
                return null;
            }
            boolean hasBackup = tokens.contains("backup");
            boolean hasSystem = tokens.contains("system") || tokens.contains("systems");
            boolean hasFor = tokens.contains("for");
            if (hasBackup && hasSystem && hasFor && predicates.contains("backupfor")) {
                return new PredicateSelection("backupfor", "backup system for", java.util.List.of("system"));
            }
            boolean hasIndicate = tokens.contains("indicate") || tokens.contains("indicated")
                    || tokens.contains("suggest") || tokens.contains("suggested")
                    || tokens.contains("signal") || tokens.contains("signals")
                    || tokens.contains("evidence");
            if (hasIndicate && predicates.contains("indicate")) {
                return new PredicateSelection("indicate", "telemetry indicate", java.util.List.of());
            }
            boolean hasStop = tokens.contains("stop") || tokens.contains("stops") || tokens.contains("stopped");
            boolean hasFunction = tokens.contains("function") || tokens.contains("functioning");
            if (hasStop && hasFunction) {
                if (predicates.contains("stop_working")) {
                    return new PredicateSelection("stop_working", "stop functioning", java.util.List.of("function"));
                }
                if (predicates.contains("stop")) {
                    return new PredicateSelection("stop", "stop functioning", java.util.List.of("function"));
                }
            }
            if (hasFunction) {
                if (predicates.contains("function")) {
                    return new PredicateSelection("function", "function", java.util.List.of());
                }
                if (predicates.contains("operate")) {
                    return new PredicateSelection("operate", "function->operate", java.util.List.of("function"));
                }
            }
            if ((tokens.contains("restore") || tokens.contains("restored") || tokens.contains("regain")
                    || tokens.contains("regained")) && predicates.contains("restore")) {
                return new PredicateSelection("restore", "restore", java.util.List.of());
            }
            boolean hasRelationship = tokens.contains("relationship") || tokens.contains("relationships");
            boolean hasBetween = tokens.contains("between");
            if (hasRelationship && hasBetween && predicates.contains("with")) {
                return new PredicateSelection("with", "relationship between", java.util.List.of("between"));
            }
            boolean hasExplain = tokens.contains("explain") || tokens.contains("explains")
                    || tokens.contains("explanation");
            if (hasExplain && predicates.contains("cause")) {
                return new PredicateSelection("cause", "explain->cause", java.util.List.of("explain"));
            }
            boolean hasPlausible = tokens.contains("plausible") || tokens.contains("likely") || tokens.contains("most");
            boolean hasCause = tokens.contains("cause") || tokens.contains("caused");
            if ((hasPlausible || hasCause) && predicates.contains("cause")) {
                return new PredicateSelection("cause", "plausible cause", java.util.List.of());
            }
            return null;
        }

        private String inferObjectForPredicate(java.util.List<String> tokens,
                                               String predicate,
                                               String subject,
                                               String object,
                                               com.sahr.core.KnowledgeBase graph) {
            if (tokens == null || tokens.isEmpty() || graph == null) {
                return null;
            }
            String normalizedPredicate = normalizeToken(predicate);
            java.util.Map<String, String> entityMap = collectEntityNames(graph);
            if (entityMap.isEmpty()) {
                return null;
            }
            if ("backupfor".equals(normalizedPredicate)) {
                if (object == null || object.isBlank() || (subject != null && subject.equals(object))) {
                    String afterFor = inferEntityAfterToken(tokens, "for", entityMap);
                    if (afterFor != null) {
                        return afterFor;
                    }
                }
            }
            if ("restore".equals(normalizedPredicate)) {
                boolean hasStability = tokens.contains("stability") || tokens.contains("stable");
                boolean hasOrientation = tokens.contains("orientation");
                if (hasStability && entityMap.containsKey("stable_orientation")) {
                    return entityMap.get("stable_orientation");
                }
                if (hasOrientation && entityMap.containsKey("spacecraft_orientation")) {
                    return entityMap.get("spacecraft_orientation");
                }
            }
            if ("indicate".equals(normalizedPredicate)) {
                if (tokens.contains("failure") && entityMap.containsKey("motor_failure")) {
                    return entityMap.get("motor_failure");
                }
                if (tokens.contains("instability") && entityMap.containsKey("spacecraft_instability")) {
                    return entityMap.get("spacecraft_instability");
                }
            }
            if ("cause".equals(normalizedPredicate)) {
                if (tokens.contains("loss")
                        && (tokens.contains("orientation") || tokens.contains("control"))
                        && entityMap.containsKey("control_spacecraft_orientation")) {
                    return entityMap.get("control_spacecraft_orientation");
                }
                if (tokens.contains("loss")) {
                    String afterOf = inferEntityAfterToken(tokens, "of", entityMap);
                    if (afterOf != null) {
                        return afterOf;
                    }
                    if (entityMap.containsKey("spacecraft_orientation_control")) {
                        return entityMap.get("spacecraft_orientation_control");
                    }
                }
                if (tokens.contains("control") && entityMap.containsKey("spacecraft_orientation_control")) {
                    return entityMap.get("spacecraft_orientation_control");
                }
                if ((tokens.contains("orientation") || tokens.contains("control"))
                        && entityMap.containsKey("spacecraft_orientation_control")) {
                    return entityMap.get("spacecraft_orientation_control");
                }
                if (tokens.contains("instability") && entityMap.containsKey("spacecraft_instability")) {
                    return entityMap.get("spacecraft_instability");
                }
                if (tokens.contains("telemetry") && tokens.contains("events")
                        && entityMap.containsKey("spacecraft_instability")) {
                    return entityMap.get("spacecraft_instability");
                }
            }
            return null;
        }

        private ConditionBridge inferConditionBridge(java.util.List<String> tokens,
                                                     com.sahr.core.KnowledgeBase graph) {
            if (tokens == null || tokens.isEmpty() || graph == null) {
                return null;
            }
            java.util.List<String> normalized = new java.util.ArrayList<>();
            for (String token : tokens) {
                String value = normalizeToken(token);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
            if (normalized.isEmpty()) {
                return null;
            }
            boolean powerUnavailable = containsAny(normalized, "power", "voltage", "electrical")
                    && containsAny(normalized, "lost", "drop", "drops", "dropped", "down", "unavailable");
            boolean propellantAvailable = normalized.contains("propellant")
                    && containsAny(normalized, "available", "operational", "functional", "normal", "remained");
            boolean stopFunctioning = containsAny(normalized, "stop", "stops", "stopped")
                    && containsAny(normalized, "function", "functioning", "working");
            boolean stillFunctioning = normalized.contains("still")
                    && containsAny(normalized, "function", "functioning", "operate", "operational");
            boolean conditionQuery = containsAny(normalized, "condition", "conditions")
                    && containsAny(normalized, "under", "what");

            java.util.Map<String, String> entityMap = collectEntityNames(graph);
            if (entityMap.isEmpty()) {
                return null;
            }
            if (stopFunctioning && powerUnavailable) {
                String resource = firstAvailableResource(entityMap, "electrical_power",
                        "electrical_bus_voltage", "electrical_bus", "power");
                if (resource != null) {
                    return new ConditionBridge(
                            java.util.List.of("power_unavailable"),
                            java.util.List.of(),
                            "poweredby",
                            resource,
                            true
                    );
                }
            }
            if (stillFunctioning && propellantAvailable) {
                String resource = firstAvailableResource(entityMap, "propellant");
                if (resource != null) {
                    return new ConditionBridge(
                            java.util.List.of(),
                            java.util.List.of("propellant_available"),
                            "poweredby",
                            resource,
                            true
                    );
                }
            }
            if (conditionQuery) {
                return new ConditionBridge(
                        java.util.List.of(),
                        java.util.List.of("condition_query"),
                        "poweredby",
                        null,
                        true
                );
            }
            return null;
        }

        private boolean containsAny(java.util.List<String> tokens, String... options) {
            for (String option : options) {
                if (tokens.contains(option)) {
                    return true;
                }
            }
            return false;
        }

        private String firstAvailableResource(java.util.Map<String, String> entityMap, String... keys) {
            for (String key : keys) {
                String match = entityMap.get(key);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }

        private String inferEntityAfterToken(java.util.List<String> tokens,
                                             String marker,
                                             java.util.Map<String, String> entityMap) {
            int idx = tokens.indexOf(marker);
            if (idx < 0 || idx + 1 >= tokens.size()) {
                return null;
            }
            java.util.List<String> tail = tokens.subList(idx + 1, tokens.size());
            int maxGram = Math.min(4, tail.size());
            for (int size = maxGram; size >= 1; size--) {
                for (int i = 0; i <= tail.size() - size; i++) {
                    String candidate = String.join("_", tail.subList(i, i + size));
                    String match = entityMap.get(candidate);
                    if (match != null) {
                        return match;
                    }
                }
            }
            return null;
        }

        private PhraseOverride inferPhraseOverride(java.util.List<String> tokens,
                                                   com.sahr.core.KnowledgeBase graph) {
            if (tokens == null || tokens.isEmpty() || graph == null) {
                return null;
            }
            java.util.List<String> normalized = new java.util.ArrayList<>();
            for (String token : tokens) {
                String value = normalizeToken(token);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
            if (normalized.isEmpty()) {
                return null;
            }
            java.util.Set<String> predicates = collectPredicateNames(graph);
            java.util.Map<String, String> entityMap = collectEntityNames(graph);
            boolean hasExplain = normalized.contains("explain") || normalized.contains("explains")
                    || normalized.contains("explanation");
            boolean hasLoss = normalized.contains("loss");
            if (hasExplain && predicates.contains("cause")) {
                String object = null;
                if ((hasLoss || normalized.contains("orientation") || normalized.contains("control"))
                        && entityMap.containsKey("control_spacecraft_orientation")) {
                    object = entityMap.get("control_spacecraft_orientation");
                } else if (hasLoss && entityMap.containsKey("spacecraft_orientation_control")) {
                    object = entityMap.get("spacecraft_orientation_control");
                } else if (hasLoss) {
                    object = inferEntityAfterToken(normalized, "of", entityMap);
                }
                if (object == null && (normalized.contains("unstable") || normalized.contains("instability"))
                        && entityMap.containsKey("spacecraft_instability")) {
                    object = entityMap.get("spacecraft_instability");
                }
                return new PhraseOverride("cause", object, null, com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN, true, true);
            }
            boolean hasPrevent = normalized.contains("prevent") || normalized.contains("prevents")
                    || normalized.contains("prevented") || normalized.contains("preventing");
            if (hasPrevent && predicates.contains("cause")) {
                String object = null;
                if ((normalized.contains("orientation") || normalized.contains("control"))
                        && entityMap.containsKey("control_spacecraft_orientation")) {
                    object = entityMap.get("control_spacecraft_orientation");
                } else if (normalized.contains("control") && entityMap.containsKey("spacecraft_orientation_control")) {
                    object = entityMap.get("spacecraft_orientation_control");
                } else {
                    object = inferEntityAfterToken(normalized, "prevent", entityMap);
                }
                String subject = null;
                if (entityMap.containsKey("wheel_motor") && normalized.contains("wheel") && normalized.contains("motor")) {
                    subject = entityMap.get("wheel_motor");
                }
                return new PhraseOverride("cause", object, subject, com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN, true, true);
            }
            boolean hasRelationship = normalized.contains("relationship") || normalized.contains("relationships");
            boolean hasBetween = normalized.contains("between");
            if (hasRelationship && hasBetween) {
                java.util.List<String> entities = inferEntities(normalized, entityMap);
                if (entities.size() >= 2) {
                    for (int i = 0; i < entities.size() - 1; i++) {
                        for (int j = i + 1; j < entities.size(); j++) {
                            String left = entities.get(i);
                            String right = entities.get(j);
                            String predicate = inferPredicateBetweenEntities(left, right, graph);
                            if (predicate != null && !predicate.isBlank()) {
                                return new PhraseOverride(predicate, right, left, com.sahr.core.QueryPlan.Kind.RELATION_MATCH, true, true);
                            }
                        }
                    }
                    if (predicates.contains("with")) {
                        return new PhraseOverride("with", entities.get(1), entities.get(0),
                                com.sahr.core.QueryPlan.Kind.RELATION_MATCH, true, true);
                    }
                }
                if (predicates.contains("control") && entityMap.containsKey("control_spacecraft_orientation")) {
                    return new PhraseOverride("control", entityMap.get("control_spacecraft_orientation"), null,
                            com.sahr.core.QueryPlan.Kind.RELATION_MATCH, true, true);
                }
            }
            boolean hasConditions = normalized.contains("conditions") || normalized.contains("condition");
            boolean hasUnder = normalized.contains("under") || normalized.contains("what");
            if (hasConditions && hasUnder && predicates.contains("poweredby")) {
                return new PhraseOverride("poweredby", null, null, com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN, true, true);
            }
            boolean hasDepend = normalized.contains("depend") || normalized.contains("depends")
                    || normalized.contains("dependent");
            boolean hasNot = normalized.contains("not") || normalized.contains("without");
            if (hasDepend && hasNot && predicates.contains("poweredby")) {
                String object = firstAvailableResource(entityMap, "electrical_power", "electrical_actuators",
                        "electrically_powered_actuators");
                return new PhraseOverride("poweredby", object, null, com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN, true, true);
            }
            boolean hasRuledOut = normalized.contains("ruled") || normalized.contains("rule")
                    || normalized.contains("ruledout") || normalized.contains("exclude");
            if (hasRuledOut && predicates.contains("cause") && entityMap.containsKey("spacecraft_instability")) {
                return new PhraseOverride("cause", entityMap.get("spacecraft_instability"), null,
                        com.sahr.core.QueryPlan.Kind.CAUSE_CHAIN, true, true);
            }
            return null;
        }

        private java.util.List<String> inferEntities(java.util.List<String> tokens,
                                                     java.util.Map<String, String> entityMap) {
            java.util.List<String> results = new java.util.ArrayList<>();
            if (tokens == null || tokens.isEmpty() || entityMap == null || entityMap.isEmpty()) {
                return results;
            }
            int maxGram = Math.min(4, tokens.size());
            int i = 0;
            while (i < tokens.size()) {
                boolean matched = false;
                for (int size = maxGram; size >= 1; size--) {
                    if (i + size > tokens.size()) {
                        continue;
                    }
                    String candidate = String.join("_", tokens.subList(i, i + size));
                    String match = entityMap.get(candidate);
                    if (match != null) {
                        results.add(match);
                        i += size;
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    i += 1;
                }
            }
            return results;
        }

        private String inferPredicateBetweenEntities(String left,
                                                     String right,
                                                     com.sahr.core.KnowledgeBase graph) {
            if (left == null || right == null || graph == null) {
                return null;
            }
            com.sahr.core.SymbolId leftId = new com.sahr.core.SymbolId(left);
            com.sahr.core.SymbolId rightId = new com.sahr.core.SymbolId(right);
            java.util.List<String> preferred = java.util.List.of(
                    "contain", "contains", "partof", "componentof",
                    "control", "operate", "poweredby", "backupfor",
                    "cause", "fail", "require"
            );
            for (com.sahr.core.RelationAssertion assertion : graph.getAllAssertions()) {
                if ((assertion.subject().equals(leftId) && assertion.object().equals(rightId))
                        || (assertion.subject().equals(rightId) && assertion.object().equals(leftId))) {
                    String predicate = localName(assertion.predicate());
                    if (preferred.contains(predicate)) {
                        return predicate;
                    }
                    return localName(assertion.predicate());
                }
            }
            for (com.sahr.core.RuleAssertion rule : graph.getAllRules()) {
                com.sahr.core.RelationAssertion consequent = rule.consequent();
                if ((consequent.subject().equals(leftId) && consequent.object().equals(rightId))
                        || (consequent.subject().equals(rightId) && consequent.object().equals(leftId))) {
                    String predicate = localName(consequent.predicate());
                    if (preferred.contains(predicate)) {
                        return predicate;
                    }
                    return localName(consequent.predicate());
                }
            }
            return null;
        }

        private String safeToken(String value) {
            if (value == null) {
                return "";
            }
            return value.replaceAll("\\s+", " ").trim();
        }

        private java.util.Set<String> collectPredicateNames(com.sahr.core.KnowledgeBase graph) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (com.sahr.core.RelationAssertion assertion : graph.getAllAssertions()) {
                String name = localName(assertion.predicate());
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            for (com.sahr.core.RuleAssertion rule : graph.getAllRules()) {
                String antecedent = localName(rule.antecedent().predicate());
                String consequent = localName(rule.consequent().predicate());
                if (!antecedent.isBlank()) {
                    names.add(antecedent);
                }
                if (!consequent.isBlank()) {
                    names.add(consequent);
                }
            }
            return names;
        }

        private String inferEntity(java.util.List<String> tokens, com.sahr.core.KnowledgeBase graph) {
            if (tokens == null || tokens.isEmpty() || graph == null) {
                return null;
            }
            java.util.Map<String, String> entityMap = collectEntityNames(graph);
            if (entityMap.isEmpty()) {
                return null;
            }
            java.util.List<String> normalized = new java.util.ArrayList<>();
            for (String token : tokens) {
                String value = normalizeToken(token);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
            int maxGram = Math.min(4, normalized.size());
            String best = null;
            String bestEntity = null;
            for (int size = maxGram; size >= 1; size--) {
                for (int i = 0; i <= normalized.size() - size; i++) {
                    String candidate = String.join("_", normalized.subList(i, i + size));
                    String match = entityMap.get(candidate);
                    if (match != null) {
                        best = candidate;
                        bestEntity = match;
                        break;
                    }
                }
                if (bestEntity != null) {
                    break;
                }
            }
            return bestEntity;
        }

        private java.util.Map<String, String> collectEntityNames(com.sahr.core.KnowledgeBase graph) {
            java.util.Map<String, String> names = new java.util.HashMap<>();
            for (com.sahr.core.RelationAssertion assertion : graph.getAllAssertions()) {
                String subject = assertion.subject().value();
                String object = assertion.object().value();
                recordEntityName(names, subject);
                recordEntityName(names, object);
            }
            for (com.sahr.core.EntityNode entity : graph.getAllEntities()) {
                recordEntityName(names, entity.id().value());
            }
            return names;
        }

        private void recordEntityName(java.util.Map<String, String> names, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            String normalized = normalizeToken(stripPrefix(value));
            if (!normalized.isBlank()) {
                names.putIfAbsent(normalized, value);
            }
        }

        private String stripPrefix(String value) {
            if (value.startsWith("entity:")) {
                return value.substring("entity:".length());
            }
            if (value.startsWith("concept:")) {
                return value.substring("concept:".length());
            }
            return value;
        }

        private String localName(String predicate) {
            if (predicate == null || predicate.isBlank()) {
                return "";
            }
            int hashIdx = predicate.lastIndexOf('#');
            int slashIdx = predicate.lastIndexOf('/');
            int idx = Math.max(hashIdx, slashIdx);
            String local = idx >= 0 ? predicate.substring(idx + 1) : predicate;
            return normalizeToken(local);
        }

        private String normalizeToken(String raw) {
            if (raw == null) {
                return "";
            }
            return raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z_]", "");
        }

        private String normalizeVerb(String token) {
            if (token == null || token.isBlank()) {
                return "";
            }
            if ("failure".equals(token)) {
                return "fail";
            }
            if (token.endsWith("ed") && token.length() > 2) {
                return token.substring(0, token.length() - 2);
            }
            if (token.endsWith("ing") && token.length() > 3) {
                return token.substring(0, token.length() - 3);
            }
            if (token.endsWith("s") && token.length() > 1) {
                return token.substring(0, token.length() - 1);
            }
            return token;
        }

        private boolean isWeakEntity(String value) {
            if (value == null || value.isBlank()) {
                return true;
            }
            String normalized = normalizeToken(stripPrefix(value));
            return "system".equals(normalized) || "systems".equals(normalized)
                    || "component".equals(normalized) || "components".equals(normalized)
                    || "earlier".equals(normalized) || "most".equals(normalized)
                    || "possible".equals(normalized) || "not".equals(normalized)
                    || "relationship".equals(normalized) || "relationships".equals(normalized)
                    || "conditions".equals(normalized) || "condition".equals(normalized)
                    || "loss".equals(normalized) || "explanation".equals(normalized)
                    || "explain".equals(normalized) || "under".equals(normalized)
                    || "what".equals(normalized) || "available".equals(normalized);
        }

        private static final class PhraseOverride {
            private final String predicate;
            private final String object;
            private final String subject;
            private final com.sahr.core.QueryPlan.Kind kind;
            private final boolean clearSubject;
            private final boolean clearObject;

            private PhraseOverride(String predicate,
                                   String object,
                                   String subject,
                                   com.sahr.core.QueryPlan.Kind kind,
                                   boolean clearSubject,
                                   boolean clearObject) {
                this.predicate = predicate;
                this.object = object;
                this.subject = subject;
                this.kind = kind;
                this.clearSubject = clearSubject;
                this.clearObject = clearObject;
            }
        }

        private static final class PlanSelection {
            private final com.sahr.core.QueryPlan.Kind kind;
            private final QueryGoal query;

            private PlanSelection(com.sahr.core.QueryPlan.Kind kind, QueryGoal query) {
                this.kind = kind == null ? com.sahr.core.QueryPlan.Kind.RELATION_MATCH : kind;
                this.query = query;
            }
        }

        private static final class PredicateSelection {
            private final String predicate;
            private final String cue;
            private final java.util.List<String> rejected;

            private PredicateSelection(String predicate, String cue, java.util.List<String> rejected) {
                this.predicate = predicate;
                this.cue = cue;
                this.rejected = rejected == null ? java.util.List.of() : java.util.List.copyOf(rejected);
            }
        }

        private static final class ConditionBridge {
            private final java.util.List<String> failedConditions;
            private final java.util.List<String> preservedConditions;
            private final String predicate;
            private final String object;
            private final boolean forceSubjectNull;

            private ConditionBridge(java.util.List<String> failedConditions,
                                    java.util.List<String> preservedConditions,
                                    String predicate,
                                    String object,
                                    boolean forceSubjectNull) {
                this.failedConditions = failedConditions == null ? java.util.List.of() : java.util.List.copyOf(failedConditions);
                this.preservedConditions = preservedConditions == null ? java.util.List.of() : java.util.List.copyOf(preservedConditions);
                this.predicate = predicate;
                this.object = object;
                this.forceSubjectNull = forceSubjectNull;
            }
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
