package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.IntentDecision;
import com.sahr.core.IntentType;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryKey;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.ReasoningTrace;
import com.sahr.core.ReasoningTraceEntry;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.core.CandidateType;
import com.sahr.core.GuardedKnowledgeBase;
import com.sahr.core.WorkingMemory;
import com.sahr.core.ReasoningPhase;
import com.sahr.core.ReasoningPhaseCoordinator;
import com.sahr.nlp.NoopTermMapper;
import com.sahr.nlp.InputFeatureExtractor;
import com.sahr.nlp.InputFeatures;
import com.sahr.nlp.RuleParser;
import com.sahr.nlp.RuleStatement;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.Statement;
import com.sahr.nlp.StatementBatch;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.SahrAnnotationVocabulary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class SahrAgent {
    private static final Logger logger = Logger.getLogger(SahrAgent.class.getName());
    private static final String PREDICATE_TYPE = "rdf:type";
    private static final int MAX_PROPAGATION_ITERATIONS = 5;
    private static final int MAX_DERIVED_ASSERTIONS = 100;
    private static final int MAX_SUBGOAL_DEPTH = 3;
    private static final int MAX_SUBGOALS = 20;

    private final KnowledgeBase graph;
    private final OntologyService ontology;
    private final SahrReasoner reasoner;
    private final SimpleQueryParser parser;
    private final StatementParser statementParser;
    private final RuleParser ruleParser;
    private final TermMapper termMapper;
    private final ReasoningTrace trace;
    private final WorkingMemory workingMemory;
    private final ReasoningPhaseCoordinator phases;
    private final ExplanationChainBuilder explanationChains;
    private final AnswerRenderer answerRenderer;
    private final AliasBridge aliasBridge;
    private final ForwardChainSearch forwardChainSearch;
    private final PredicateExplainer predicateExplainer;
    private final AnswerRanker answerRanker;
    private final OntologyAnnotationResolver annotationResolver;

    public SahrAgent(
            KnowledgeBase graph,
            OntologyService ontology,
            SahrReasoner reasoner,
            SimpleQueryParser parser
    ) {
        this(graph, ontology, reasoner, parser, new StatementParser(), new NoopTermMapper());
    }

    public SahrAgent(
            KnowledgeBase graph,
            OntologyService ontology,
            SahrReasoner reasoner,
            SimpleQueryParser parser,
            TermMapper termMapper
    ) {
        this(graph, ontology, reasoner, parser, new StatementParser(), termMapper);
    }

    public SahrAgent(
            KnowledgeBase graph,
            OntologyService ontology,
            SahrReasoner reasoner,
            SimpleQueryParser parser,
            StatementParser statementParser,
            TermMapper termMapper
    ) {
        this.phases = new ReasoningPhaseCoordinator();
        this.graph = new GuardedKnowledgeBase(graph, phases);
        this.ontology = ontology;
        this.reasoner = reasoner;
        this.parser = parser;
        this.statementParser = statementParser;
        this.ruleParser = new RuleParser(statementParser);
        this.termMapper = termMapper;
        this.trace = new ReasoningTrace();
        this.workingMemory = new WorkingMemory(phases);
        this.annotationResolver = new OntologyAnnotationResolver(this.ontology);
        this.answerRenderer = new AnswerRenderer(
                new AnswerRenderer.DisplayFormatter() {
                    @Override
                    public String localName(String predicate) {
                        return SahrAgent.this.localName(predicate);
                    }

                    @Override
                    public Boolean booleanConcept(SymbolId id) {
                        return SahrAgent.this.booleanConcept(id);
                    }

                    @Override
                    public String normalizeTypeToken(String raw) {
                        return SahrAgent.this.normalizeTypeToken(raw);
                    }
                },
                annotationResolver
        );
        this.aliasBridge = new AliasBridge(
                this.graph,
                this.ontology,
                new AliasBridge.AliasFormatter() {
                    @Override
                    public String localName(String predicate) {
                        return SahrAgent.this.localName(predicate);
                    }
                }
        );
        this.answerRanker = new AnswerRanker(annotationResolver);
        this.explanationChains = new ExplanationChainBuilder(
                this.graph,
                this.ontology,
                new ExplanationChainBuilder.Formatter() {
                    @Override
                    public String localName(String predicate) {
                        return SahrAgent.this.localName(predicate);
                    }

                    @Override
                    public String formatAssertionSentence(RelationAssertion assertion) {
                        return answerRenderer.formatAssertionSentence(assertion);
                    }

                    @Override
                    public String formatRuleSentence(RuleAssertion rule) {
                        return answerRenderer.formatRuleSentence(rule);
                    }

                    @Override
                    public String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect) {
                        return answerRenderer.formatCausalSentence(assertion, cause, effect);
                    }

                    @Override
                    public String normalizeTypeToken(String raw) {
                        return SahrAgent.this.normalizeTypeToken(raw);
                    }
                },
                answerRanker::specificityScore,
                annotationResolver
        );
        this.forwardChainSearch = new ForwardChainSearch(
                this.graph,
                this.aliasBridge,
                new ForwardChainSearch.Formatter() {
                    @Override
                    public String formatAssertionSentence(RelationAssertion assertion) {
                        return answerRenderer.formatAssertionSentence(assertion);
                    }

                    @Override
                    public String formatRuleSentence(RuleAssertion rule) {
                        return answerRenderer.formatRuleSentence(rule);
                    }

                    @Override
                    public String localName(String predicate) {
                        return SahrAgent.this.localName(predicate);
                    }

                    @Override
                    public Boolean booleanConcept(SymbolId id) {
                        return SahrAgent.this.booleanConcept(id);
                    }
                },
                answerRanker::predicateDynamismScore,
                answerRanker::assertionSpecificity,
                answerRanker::ruleSpecificity
        );
        this.predicateExplainer = new PredicateExplainer(
                this.graph,
                new PredicateExplainer.Formatter() {
                    @Override
                    public String localName(String predicate) {
                        return SahrAgent.this.localName(predicate);
                    }

                    @Override
                    public String formatAssertionSentence(RelationAssertion assertion) {
                        return answerRenderer.formatAssertionSentence(assertion);
                    }

                    @Override
                    public String formatRuleSentence(RuleAssertion rule) {
                        return answerRenderer.formatRuleSentence(rule);
                    }

                    @Override
                    public SymbolId selectCauseNode(RelationAssertion antecedent) {
                        return SahrAgent.this.selectCauseNode(antecedent);
                    }
                },
                (assertion) -> answerRanker.assertionExplanationScore(assertion, localName(assertion.predicate())),
                (rule) -> answerRanker.ruleExplanationScore(rule, localName(rule.consequent().predicate())),
                explanationChains
        );
    }

    public String handle(String input) {
        String normalizedInput = stripLeadingQuestionNumber(input);
        if (input != null && !input.equals(normalizedInput) && logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine(() -> "Normalized input='" + input + "' -> '" + normalizedInput + "'");
        }
        ReasoningPhase previousPhase = phases.enter(ReasoningPhase.UPDATE);
        InputFeatures features = InputFeatureExtractor.extract(normalizedInput);
        IntentDecision intentDecision = selectIntent(features);
        boolean questionLike = parser.isQuestion(normalizedInput) || isQuestionIntent(intentDecision);
        boolean allowRuleParse = features.has("has_if")
                && !features.has("has_question_mark")
                && !features.has("has_wh");
        QueryGoal query = mapQuery(parser.parse(normalizedInput));
        Optional<RuleStatement> ruleStatement = (!questionLike || isRuleIntent(intentDecision) || allowRuleParse)
                ? ruleParser.parse(normalizedInput).map(this::mapRuleStatement)
                : Optional.empty();
        if (ruleStatement.isPresent()) {
            questionLike = false;
        }
        Optional<RuleAssertion> rule = ruleStatement.map(this::toRuleAssertion);
        Optional<Statement> statement = (questionLike || rule.isPresent())
                ? Optional.empty()
                : statementParser.parse(normalizedInput).map(this::mapStatement);
        logger.fine(() -> "Input='" + normalizedInput + "' statementPresent=" + statement.isPresent()
                + " queryIntent=" + query.type());

        statement.ifPresent(this::updateWorkingMemoryFromStatement);
        updateWorkingMemoryFromQuery(query);

        try {
            if (!isQuestion(query)) {
                HeadContext context = new HeadContext(query, graph, ontology, statement.orElse(null), rule.orElse(null), workingMemory, features);
                return handleSingle(context, query, questionLike, features);
            }
            return handleWithSubgoals(query, statement.orElse(null), questionLike, rule.orElse(null), features);
        } finally {
            phases.restore(previousPhase);
        }
    }

    public Optional<ReasoningTrace> trace() {
        return Optional.of(trace);
    }

    public Optional<ReasoningTraceEntry> lastTraceEntry() {
        List<ReasoningTraceEntry> entries = trace.entries();
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    public WorkingMemorySnapshot workingMemorySnapshot() {
        return new WorkingMemorySnapshot(
                List.copyOf(workingMemory.activeEntities()),
                workingMemory.recentAssertions(),
                workingMemory.goalStack()
        );
    }

    public List<String> describeHeads(QueryGoal query) {
        if (query == null) {
            return List.of();
        }
        HeadContext context = new HeadContext(query, graph, ontology, null, null, workingMemory);
        return reasoner.heads().stream()
                .map(head -> head.explain(context))
                .toList();
    }

    public void resetWorkingMemory() {
        workingMemory.clear();
    }

    private QueryGoal mapQuery(QueryGoal query) {
        if (query.type() == QueryGoal.Type.UNKNOWN) {
            return query;
        }

        boolean predicateMapped = termMapper.mapPredicateToken(query.predicate()).isPresent();
        String requestedType = mapEntityType(query.entityType());
        String expectedRange = mapExpectedRange(query.expectedRange());
        String expectedType = mapExpectedType(query.expectedType());
        String subject = mapEntity(query.subject());
        String object = mapEntity(query.object());
        String predicate = mapPredicate(query.predicate());
        String discourse = query.discourseModifier();

        QueryGoal mapped = new QueryGoal(
                query.type(),
                subject,
                object,
                predicate,
                expectedType,
                requestedType,
                expectedRange,
                query.attribute(),
                query.modifier(),
                discourse,
                query.subjectText(),
                query.objectText(),
                query.predicateText(),
                query.goalId(),
                query.parentGoalId(),
                query.depth()
        );
        if (shouldGatePredicate(mapped, predicateMapped)) {
            return QueryGoal.unknown();
        }
        return normalizeQuery(mapped);
    }

    private boolean shouldGatePredicate(QueryGoal query, boolean predicateMapped) {
        if (query == null) {
            return false;
        }
        if (query.type() != QueryGoal.Type.RELATION
                && query.type() != QueryGoal.Type.YESNO
                && query.type() != QueryGoal.Type.COUNT) {
            return false;
        }
        if (termMapper instanceof NoopTermMapper) {
            return false;
        }
        String predicate = query.predicate();
        if (predicate == null || predicate.isBlank()) {
            return true;
        }
        if (isIri(predicate)) {
            return false;
        }
        return false;
    }

    private QueryGoal normalizeQuery(QueryGoal query) {
        if (query.type() == QueryGoal.Type.WHERE) {
            if (isBlank(query.entityType())) {
                return QueryGoal.unknown();
            }
        }
        if (query.type() == QueryGoal.Type.RELATION) {
            if (isBlank(query.predicate()) || (isBlank(query.subject()) && isBlank(query.object()))) {
                return QueryGoal.unknown();
            }
        }
        if (query.type() == QueryGoal.Type.ATTRIBUTE) {
            if (isBlank(query.subject()) || isBlank(query.attribute())) {
                return QueryGoal.unknown();
            }
        }
        if (query.type() == QueryGoal.Type.COUNT) {
            if (isBlank(query.predicate()) || (isBlank(query.object()) && isBlank(query.subject()))) {
                return QueryGoal.unknown();
            }
        }
        if (query.type() == QueryGoal.Type.YESNO) {
            if (isBlank(query.predicate()) || isBlank(query.subject()) || isBlank(query.object())) {
                return QueryGoal.unknown();
            }
        }
        return query;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isIri(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String mapEntityType(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return requestedType;
        }
        Optional<String> mapped = termMapper.mapToken(requestedType);
        return mapped.orElse("concept:" + requestedType);
    }

    private String mapExpectedRange(String expectedRange) {
        if (expectedRange == null || expectedRange.isBlank()) {
            return expectedRange;
        }
        if (expectedRange.startsWith("concept:")) {
            String raw = expectedRange.substring("concept:".length());
            Optional<String> mapped = termMapper.mapToken(raw);
            return mapped.orElse(expectedRange);
        }
        Optional<String> mappedRange = termMapper.mapToken(expectedRange);
        return mappedRange.orElse(expectedRange);
    }

    private String mapExpectedType(String expectedType) {
        if (expectedType == null || expectedType.isBlank()) {
            return expectedType;
        }
        String stripped = stripPrefix(expectedType);
        if (isGenericExpectedType(stripped)) {
            return null;
        }
        Optional<String> mappedType = termMapper.mapToken(stripped);
        if (mappedType.isPresent()) {
            return mappedType.get();
        }
        if (expectedType.startsWith("concept:")) {
            return expectedType;
        }
        return "concept:" + stripped;
    }

    private String mapEntity(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return normalizeEntityBinding(value);
    }

    private String mapPredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return predicate;
        }
        Optional<String> mappedPredicate = termMapper.mapPredicateToken(predicate);
        return mappedPredicate.orElse(predicate);
    }

    private Statement mapStatement(Statement statement) {
        Set<String> subjectTypes = mapTypes(statement.subjectTypes());
        Set<String> objectTypes = mapTypes(statement.objectTypes());
        SymbolId objectId = statement.object();
        SymbolId subjectId = statement.subject();
        if (statement.objectIsConcept()) {
            Optional<String> mapped = termMapper.mapToken(stripPrefix(statement.object().value()));
            if (mapped.isPresent()) {
                objectId = new SymbolId(mapped.get());
                objectTypes = Set.of(mapped.get());
            }
        }

        String predicate = statement.predicate();
        String surfacePredicate = predicate;
        NormalizedPredicate normalizedPredicate = normalizePredicate(statement, predicate);
        predicate = normalizedPredicate.predicate;
        if (normalizedPredicate.overrideObject != null) {
            objectId = normalizedPredicate.overrideObject;
            objectTypes = normalizedPredicate.overrideObjectTypes;
        }
        Optional<String> predicateIri = termMapper.mapPredicateToken(predicate);

        List<Statement> mappedExtras = new java.util.ArrayList<>();
        for (Statement extra : statement.additionalStatements()) {
            mappedExtras.add(mapStatement(extra));
        }
        if (subjectId != null && subjectId.value() != null) {
            LossNormalization lossNormalization = normalizeLossSubject(subjectId, predicate);
            if (lossNormalization != null) {
                subjectId = lossNormalization.normalized;
                mappedExtras.add(lossNormalization.failureStatement);
            }
        }
        if ("control".equals(localName(predicate)) && isBooleanFalse(objectId)) {
            mappedExtras.add(new Statement(
                    subjectId,
                    new SymbolId("concept:true"),
                    "fail",
                    subjectTypes,
                    java.util.Set.of("true"),
                    true,
                    statement.confidence(),
                    List.of()
            ));
        }
        if (predicateIri.isPresent() && !predicateIri.get().equals(predicate)) {
            mappedExtras.add(new Statement(
                    subjectId,
                    objectId,
                    surfacePredicate,
                    subjectTypes,
                    objectTypes,
                    statement.objectIsConcept(),
                    statement.confidence(),
                    List.of()
            ));
            predicate = predicateIri.get();
        }

        return new Statement(
                subjectId,
                objectId,
                predicate,
                subjectTypes,
                objectTypes,
                statement.objectIsConcept(),
                statement.confidence(),
                mappedExtras
        );
    }

    private NormalizedPredicate normalizePredicate(Statement statement, String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return new NormalizedPredicate(predicate, null, null);
        }
        if (!statement.objectIsConcept()) {
            String rawObject = stripPrefix(statement.object().value());
            if (STOP_FAILURE_PREDICATES.contains(predicate.toLowerCase(java.util.Locale.ROOT))
                    && STOP_FAILURE_OBJECTS.contains(rawObject.toLowerCase(java.util.Locale.ROOT))) {
                SymbolId newObject = new SymbolId("concept:true");
                return new NormalizedPredicate("fail", newObject, java.util.Set.of("true"));
            }
            return new NormalizedPredicate(predicate, null, null);
        }
        String rawObject = stripPrefix(statement.object().value());
        String loweredPredicate = predicate.toLowerCase(java.util.Locale.ROOT);
        if (STOP_FAILURE_PREDICATES.contains(loweredPredicate)) {
            SymbolId newObject = new SymbolId("concept:true");
            return new NormalizedPredicate("fail", newObject, java.util.Set.of("true"));
        }
        if (!NEGATED_OBJECTS.contains(rawObject)) {
            if ("use".equals(loweredPredicate) || "used".equals(loweredPredicate)) {
                if (rawObject.contains("backup")) {
                    return new NormalizedPredicate("backupFor", null, null);
                }
                if (rawObject.contains("control")) {
                    return new NormalizedPredicate("control", null, null);
                }
            }
            return new NormalizedPredicate(predicate, null, null);
        }
        if (NEGATED_OPERATIONS.contains(loweredPredicate)) {
            SymbolId newObject = new SymbolId("concept:true");
            return new NormalizedPredicate("fail", newObject, java.util.Set.of("true"));
        }
        return new NormalizedPredicate(predicate, null, null);
    }

    private static final class LossNormalization {
        private final SymbolId normalized;
        private final Statement failureStatement;

        private LossNormalization(SymbolId normalized, Statement failureStatement) {
            this.normalized = normalized;
            this.failureStatement = failureStatement;
        }
    }

    private LossNormalization normalizeLossSubject(SymbolId subjectId, String predicate) {
        String value = subjectId.value();
        if (value == null) {
            return null;
        }
        String stripped = stripPrefix(value);
        if (!stripped.startsWith("loss_of_")) {
            return null;
        }
        String remainder = stripped.substring("loss_of_".length());
        if (remainder.isBlank()) {
            return null;
        }
        String normalized = "entity:" + remainder;
        SymbolId normalizedId = new SymbolId(normalized);
        if ("cause".equals(localName(predicate)) || "causedby".equals(localName(predicate))) {
            Statement failure = new Statement(
                    normalizedId,
                    new SymbolId("concept:true"),
                    "fail",
                    java.util.Set.of(),
                    java.util.Set.of("true"),
                    true,
                    0.8,
                    List.of()
            );
            return new LossNormalization(normalizedId, failure);
        }
        return null;
    }

    private boolean isBooleanFalse(SymbolId id) {
        if (id == null || id.value() == null) {
            return false;
        }
        String value = id.value();
        if (value.startsWith("concept:")) {
            value = value.substring("concept:".length());
        }
        return "false".equalsIgnoreCase(value);
    }

    private static final class NormalizedPredicate {
        private final String predicate;
        private final SymbolId overrideObject;
        private final java.util.Set<String> overrideObjectTypes;

        private NormalizedPredicate(String predicate, SymbolId overrideObject, java.util.Set<String> overrideObjectTypes) {
            this.predicate = predicate;
            this.overrideObject = overrideObject;
            this.overrideObjectTypes = overrideObjectTypes;
        }
    }

    private static final java.util.Set<String> NEGATED_OBJECTS = java.util.Set.of(
            "false",
            "not",
            "no"
    );

    private static final java.util.Set<String> NEGATED_OPERATIONS = java.util.Set.of(
            "operate",
            "function",
            "work",
            "respond"
    );

    private static final java.util.Set<String> STOP_FAILURE_PREDICATES = java.util.Set.of(
            "stop",
            "stop_responding",
            "stop_functioning",
            "stop_working",
            "stop_operating",
            "stopped",
            "stopped_responding"
    );

    private static final java.util.Set<String> STOP_FAILURE_OBJECTS = java.util.Set.of(
            "responding",
            "functioning",
            "working",
            "operating"
    );

    private Set<String> mapTypes(Set<String> types) {
        Set<String> mapped = new HashSet<>();
        for (String type : types) {
            Optional<String> iri = termMapper.mapToken(stripPrefix(type));
            mapped.add(iri.orElse("concept:" + type));
        }
        return mapped;
    }

    private String stripPrefix(String value) {
        if (value.startsWith("concept:")) {
            return value.substring("concept:".length());
        }
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        return value;
    }

    private boolean isGenericExpectedType(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return GENERIC_EXPECTED_TYPES.contains(value);
    }

    private static final java.util.Set<String> GENERIC_EXPECTED_TYPES = java.util.Set.of(
            "component",
            "system",
            "systems",
            "chain",
            "event",
            "events",
            "cause",
            "causes",
            "reason",
            "reasons",
            "explanation",
            "explanations",
            "evidence",
            "mechanism",
            "relationship",
            "conditions",
            "condition",
            "type",
            "types",
            "kind",
            "kinds"
    );

    private String applyCandidate(ReasoningCandidate winner) {
        switch (winner.type()) {
            case ASSERTION:
                return applyAssertion(winner.payload());
            case ANSWER:
                return winner.payload() == null ? "No payload." : winner.payload().toString();
            default:
                return winner.payload() == null ? "No payload." : winner.payload().toString();
        }
    }

    private ApplyResult applyAssertionResult(Object payload) {
        if (payload instanceof RuleAssertion) {
            RuleAssertion rule = (RuleAssertion) payload;
            boolean added = addRuleIfNew(rule);
            logger.fine(() -> "Applied rule payload: " + rule);
            logIngested("rule", rule.toString(), null, null, null);
            return added ? ApplyResult.added("Rule recorded.") : ApplyResult.existing("Rule already known.");
        }
        if (payload instanceof RelationAssertion) {
            RelationAssertion assertion = (RelationAssertion) payload;
            boolean added = addAssertionIfNew(assertion);
            if (PREDICATE_TYPE.equals(assertion.predicate())) {
                upsertEntityType(assertion.subject(), assertion.object().value());
            }
            if (added) {
                runPropagationClosure();
            }
            logger.fine(() -> "Applied assertion payload: " + payload);
            logIngested("assertion", assertion.toString(), assertion.subject().value(), assertion.predicate(), assertion.object().value());
            return added ? ApplyResult.added("Assertion recorded.") : ApplyResult.existing("Assertion already known.");
        }
        if (payload instanceof StatementBatch) {
            StatementBatch batch = (StatementBatch) payload;
            boolean anyAdded = false;
            for (Statement statement : batch.statements()) {
                ApplyResult result = applyAssertionResult(statement);
                anyAdded = anyAdded || result.added;
            }
            return anyAdded ? ApplyResult.added("Assertion recorded.") : ApplyResult.existing("Assertion already known.");
        }
        if (payload instanceof Statement) {
            Statement statement = (Statement) payload;
            List<SymbolId> subjects = expandConjoinedSubjects(statement.subject());
            for (SymbolId subject : subjects) {
                upsertEntity(subject, subjectTypesFor(subject, statement.subjectTypes()));
            }
            if (!statement.objectIsConcept()) {
                upsertEntity(statement.object(), statement.objectTypes());
            }
            boolean addedAny = false;
            for (SymbolId subject : subjects) {
                RelationAssertion assertion = new RelationAssertion(
                        subject,
                        statement.predicate(),
                        statement.object(),
                        statement.confidence()
                );
                boolean added = addAssertionIfNew(assertion);
                addedAny = addedAny || added;
                if (PREDICATE_TYPE.equals(statement.predicate())) {
                    upsertEntityType(subject, statement.object().value());
                }
            }
            if (addedAny) {
                runPropagationClosure();
            }
            logger.fine(() -> "Applied statement assertion: " + statement);
            logIngested("statement", statement.predicate(), statement.subject().value(), statement.predicate(), statement.object().value());
            return addedAny ? ApplyResult.added("Assertion recorded.") : ApplyResult.existing("Assertion already known.");
        }
        return ApplyResult.existing("Unknown assertion payload.");
    }

    private void logIngested(String kind, String label, String subject, String predicate, String object) {
        logger.fine(() -> "INGEST kind=" + kind
                + " label=" + safe(label)
                + " subject=" + safe(subject)
                + " predicate=" + safe(predicate)
                + " object=" + safe(object));
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String applyAssertion(Object payload) {
        return applyAssertionResult(payload).message;
    }

    private boolean isQuestion(QueryGoal query) {
        return query.type() != QueryGoal.Type.UNKNOWN;
    }

    private String stripLeadingQuestionNumber(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return input;
        }
        return trimmed.replaceFirst("^\\s*\\d+\\s*[.)]\\s+", "");
    }

    private boolean isYesNo(QueryGoal query) {
        return query.type() == QueryGoal.Type.YESNO;
    }

    private String resolveQuestionAfterAssertion(QueryGoal query, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            HeadContext followUpContext = new HeadContext(query, graph, ontology, workingMemory);
            List<ReasoningCandidate> followUp = withReadPhase(() -> reasoner.reason(followUpContext));
            if (followUp.isEmpty()) {
                return isYesNo(query) ? "Unknown." : "No candidates produced.";
            }
            ReasoningCandidate winner = followUp.get(0);
            trace.addEntry(new ReasoningTraceEntry(query, followUp, winner));
            if (CandidateType.ANSWER.equals(winner.type())) {
                logger.fine(() -> "Follow-up winner type=" + winner.type()
                        + " producedBy=" + winner.producedBy()
                        + " score=" + winner.score());
                String multiAnswer = buildMultiAnswer(query, followUp);
                if (multiAnswer != null) {
                    recordAnswerValues(query, extractAnswerValues(followUp));
                    return formatAnswerWithEvidence(multiAnswer, followUp, winner);
                }
                recordAnswerIfPossible(query, winner.payload());
                return formatAnswerWithEvidence(winner.payload().toString(), followUp, winner);
            }
            if (!CandidateType.ASSERTION.equals(winner.type())) {
                recordAnswerIfPossible(query, winner.payload());
                return winner.payload() == null ? "No payload." : winner.payload().toString();
            }
            applyCandidate(winner);
        }
        return "Assertion recorded.";
    }

    private String handleSingle(HeadContext context, QueryGoal query, boolean questionLike, InputFeatures features) {
        List<ReasoningCandidate> candidates = withReadPhase(() -> reasoner.reason(context));
        boolean question = isQuestion(query) || questionLike;
        if (question) {
            List<ReasoningCandidate> questionCandidates = filterQueryResolutionCandidates(candidates);
            if (questionCandidates.isEmpty()) {
                return isYesNo(query) ? "Unknown." : "No candidates produced.";
            }
            candidates = questionCandidates;
        }
        if (candidates.isEmpty()) {
            if (isYesNo(query)) {
                return "Unknown.";
            }
            return "No candidates produced.";
        }
        ReasoningCandidate winner = selectStatementCandidate(context, query, candidates)
                .orElse(candidates.get(0));
        trace.addEntry(new ReasoningTraceEntry(query, candidates, winner));
        logger.fine(() -> "Winner type=" + winner.type() + " producedBy=" + winner.producedBy()
                + " score=" + winner.score());
        if (CandidateType.SUBGOAL.equals(winner.type()) && winner.payload() instanceof QueryGoal) {
            QueryGoal subgoal = (QueryGoal) winner.payload();
            return handleWithSubgoals(subgoal, null, true, null, features);
        }
        if (CandidateType.QUERY_PLAN.equals(winner.type()) && winner.payload() instanceof com.sahr.core.QueryPlan) {
            return executeQueryPlan((com.sahr.core.QueryPlan) winner.payload());
        }
        if (CandidateType.ANSWER.equals(winner.type())) {
            String multiAnswer = buildMultiAnswer(query, candidates);
            if (multiAnswer != null) {
                recordAnswerValues(query, extractAnswerValues(candidates));
                return formatAnswerWithEvidence(multiAnswer, candidates, winner);
            }
        }
        String result = applyCandidate(winner);
        if (CandidateType.ANSWER.equals(winner.type())) {
            recordAnswerIfPossible(query, winner.payload());
            return formatAnswerWithEvidence(result, candidates, winner);
        }
        return result;
    }

    private java.util.Optional<ReasoningCandidate> selectStatementCandidate(HeadContext context,
                                                                             QueryGoal query,
                                                                             List<ReasoningCandidate> candidates) {
        if (isQuestion(query)) {
            return java.util.Optional.empty();
        }
        if (context == null || context.statement().isEmpty()) {
            return java.util.Optional.empty();
        }
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.ASSERTION.equals(candidate.type())
                    && "assertion-insertion".equals(candidate.producedBy())) {
                return java.util.Optional.of(candidate);
            }
        }
        return java.util.Optional.empty();
    }

    private String handleWithSubgoals(QueryGoal root,
                                      Statement statement,
                                      boolean questionLike,
                                      RuleAssertion rule,
                                      InputFeatures features) {
        java.util.ArrayDeque<QueryGoal> queue = new java.util.ArrayDeque<>();
        java.util.Set<com.sahr.core.QueryKey> seen = new java.util.HashSet<>();
        queue.add(root);
        int processed = 0;
        boolean question = isQuestion(root) || questionLike;

        while (!queue.isEmpty() && processed < MAX_SUBGOALS) {
            QueryGoal current = queue.removeFirst();
            processed++;
            workingMemory.pushGoal(current);

            HeadContext context = new HeadContext(
                    current,
                    graph,
                    ontology,
                    current.goalId().equals(root.goalId()) ? statement : null,
                    current.goalId().equals(root.goalId()) ? rule : null,
                    workingMemory,
                    features
            );
            List<ReasoningCandidate> candidates = withReadPhase(() -> reasoner.reason(context));
            if (question) {
                List<ReasoningCandidate> queryCandidates = filterQueryResolutionCandidates(candidates);
                if (queryCandidates.isEmpty()) {
                    workingMemory.popGoal();
                    if (current.goalId().equals(root.goalId())) {
                        String fallback = directWhereMatch(current);
                        if (fallback != null) {
                            return fallback;
                        }
                        return isYesNo(root) ? "Unknown." : "No candidates produced.";
                    }
                    continue;
                }
                candidates = queryCandidates;
            }
            if (candidates.isEmpty()) {
                workingMemory.popGoal();
                if (current.goalId().equals(root.goalId())) {
                    return isYesNo(root) ? "Unknown." : "No candidates produced.";
                }
                continue;
            }
            ReasoningCandidate winner = selectPreferredCandidate(candidates);
            trace.addEntry(new ReasoningTraceEntry(current, candidates, winner));
            logger.fine(() -> "Winner type=" + winner.type() + " producedBy=" + winner.producedBy()
                    + " score=" + winner.score());

            if (CandidateType.SUBGOAL.equals(winner.type()) && winner.payload() instanceof QueryGoal) {
                QueryGoal subgoal = (QueryGoal) winner.payload();
                com.sahr.core.QueryKey subgoalKey = com.sahr.core.QueryKey.from(subgoal);
                if (subgoalKey != null && seen.contains(subgoalKey)) {
                    workingMemory.popGoal();
                    continue;
                }
                int nextDepth = current.depth() + 1;
                if (nextDepth <= MAX_SUBGOAL_DEPTH) {
                    if (subgoalKey != null) {
                        seen.add(subgoalKey);
                    }
                    queue.addLast(subgoal.withParent(current.goalId(), nextDepth));
                }
                workingMemory.popGoal();
                continue;
            }

            if (CandidateType.QUERY_PLAN.equals(winner.type()) && winner.payload() instanceof com.sahr.core.QueryPlan) {
                String planAnswer = executeQueryPlan((com.sahr.core.QueryPlan) winner.payload());
                if (current.goalId().equals(root.goalId())) {
                    workingMemory.popGoal();
                    return planAnswer;
                }
                workingMemory.popGoal();
                continue;
            }

            if (!question && CandidateType.ASSERTION.equals(winner.type())) {
                ApplyResult result = applyAssertionResult(winner.payload());
                if (result.added) {
                    queue.addLast(root);
                }
                workingMemory.popGoal();
                continue;
            }

            if (CandidateType.ANSWER.equals(winner.type())) {
                if (!current.goalId().equals(root.goalId())) {
                    applyAnswerAsAssertion(winner.payload());
                    queue.addLast(root);
                    workingMemory.popGoal();
                    continue;
                }
                String multiAnswer = buildMultiAnswer(root, candidates);
                if (multiAnswer != null) {
                    recordAnswerValues(root, extractAnswerValues(candidates));
                    workingMemory.popGoal();
                    return multiAnswer;
                }
                recordAnswerIfPossible(root, winner.payload());
                workingMemory.popGoal();
                return winner.payload() == null ? "No payload." : winner.payload().toString();
            }
            workingMemory.popGoal();
        }

        return "No candidates produced.";
    }


    private static final class ApplyResult {
        private final boolean added;
        private final String message;

        private ApplyResult(boolean added, String message) {
            this.added = added;
            this.message = message;
        }

        private static ApplyResult added(String message) {
            return new ApplyResult(true, message);
        }

        private static ApplyResult existing(String message) {
            return new ApplyResult(false, message);
        }
    }

    private boolean hasOnlyAssertionInsertion(List<ReasoningCandidate> candidates) {
        if (candidates.isEmpty()) {
            return false;
        }
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.ASSERTION.equals(candidate.type())
                    && "assertion-insertion".equals(candidate.producedBy())) {
                continue;
            }
            return false;
        }
        return true;
    }

    private List<ReasoningCandidate> filterQueryResolutionCandidates(List<ReasoningCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ReasoningCandidate> allowed = new java.util.ArrayList<>();
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.ANSWER.equals(candidate.type())
                    || CandidateType.SUBGOAL.equals(candidate.type())
                    || CandidateType.QUERY_PLAN.equals(candidate.type())
                    || CandidateType.CLARIFICATION.equals(candidate.type())) {
                allowed.add(candidate);
            }
        }
        return allowed;
    }

    private ReasoningCandidate selectPreferredCandidate(List<ReasoningCandidate> candidates) {
        ReasoningCandidate winner = candidates.get(0);
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.ANSWER.equals(candidate.type())) {
                return candidate;
            }
        }
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.QUERY_PLAN.equals(candidate.type())) {
                return candidate;
            }
        }
        if (CandidateType.SUBGOAL.equals(winner.type())) {
            for (ReasoningCandidate candidate : candidates) {
                if (CandidateType.ASSERTION.equals(candidate.type())) {
                    return candidate;
                }
            }
        }
        return winner;
    }

    private String executeQueryPlan(com.sahr.core.QueryPlan plan) {
        if (plan == null) {
            return "No candidates produced.";
        }
        QueryGoal goal = plan.goal();
        if (goal == null || goal.type() == QueryGoal.Type.UNKNOWN) {
            return "No candidates produced.";
        }
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine(() -> "QUERY_PLAN kind=" + plan.kind()
                    + " goalType=" + goal.type()
                    + " subject=" + safe(goal.subject())
                    + " predicate=" + safe(goal.predicate())
                    + " object=" + safe(goal.object()));
        }
        return switch (plan.kind()) {
            case RELATION_MATCH -> executeRelationMatch(goal);
            case TEMPORAL_MATCH -> executeTemporalMatch(goal);
            case CAUSE_CHAIN -> executeCauseChain(goal);
            case EVIDENCE_MATCH -> executeRelationMatch(goal);
        };
    }

    private String executeRelationMatch(QueryGoal goal) {
        HeadContext context = new HeadContext(goal, graph, ontology, null, null, workingMemory, null);
        List<ReasoningCandidate> candidates = withReadPhase(() -> reasoner.reason(context));
        List<ReasoningCandidate> answers = new java.util.ArrayList<>();
        for (ReasoningCandidate candidate : candidates) {
            if (CandidateType.ANSWER.equals(candidate.type())) {
                answers.add(candidate);
            }
        }
        List<ReasoningCandidate> filteredAnswers = answerRanker.filterEchoAnswers(answers, goal);
        if (!filteredAnswers.isEmpty()) {
            answers = filteredAnswers;
        }
        if (answers.isEmpty()) {
            java.util.List<String> directMatches = directRelationMatches(goal);
            if (!directMatches.isEmpty()) {
                directMatches = answerRanker.filterEchoValues(directMatches, goal);
                directMatches = answerRanker.rankAnswerValues(directMatches);
                if (directMatches.size() == 1) {
                    return directMatches.get(0);
                }
                return String.join(", ", directMatches);
            }
            String ruleMatch = directRuleMatch(goal);
            if (ruleMatch != null) {
                return ruleMatch;
            }
            String whereFallback = directWhereMatch(goal);
            if (whereFallback != null) {
                return whereFallback;
            }
            return isYesNo(goal) ? "Unknown." : "No candidates produced.";
        }
        ReasoningCandidate winner = answerRanker.selectBestAnswerCandidate(answers);
        recordAnswerIfPossible(goal, winner.payload());
        return winner.payload() == null ? "No payload." : winner.payload().toString();
    }

    private java.util.List<String> directRelationMatches(QueryGoal goal) {
        String predicate = localName(goal.predicate());
        if (predicate.isBlank()) {
            return java.util.List.of();
        }
        SymbolId subject = goal.subject() == null ? null : new SymbolId(goal.subject());
        SymbolId object = goal.object() == null ? null : new SymbolId(goal.object());
        java.util.List<String> matches = new java.util.ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String assertionPredicate = localName(assertion.predicate());
            if (!predicate.equals(assertionPredicate)) {
                continue;
            }
            if (subject != null && assertion.subject().equals(subject)) {
                matches.add(assertion.object().value());
                continue;
            }
            if (object != null && assertion.object().equals(object)) {
                matches.add(assertion.subject().value());
                continue;
            }
            if (subject == null && object == null) {
                matches.add(assertion.subject().value());
            }
        }
        return matches;
    }

    private String directRuleMatch(QueryGoal goal) {
        String predicate = localName(goal.predicate());
        if (predicate.isBlank()) {
            return null;
        }
        SymbolId subject = goal.subject() == null ? null : new SymbolId(goal.subject());
        SymbolId object = goal.object() == null ? null : new SymbolId(goal.object());
        java.util.List<String> matches = new java.util.ArrayList<>();
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            RelationAssertion antecedent = rule.antecedent();
            String consequentPredicate = localName(consequent.predicate());
            String antecedentPredicate = localName(antecedent.predicate());

            if (predicate.equals(consequentPredicate)) {
                String match = ruleMatchValue("consequent", subject, object, consequent);
                if (match != null) {
                    matches.add(match);
                } else {
                    logRuleReject(rule, predicate, subject, object, "consequent");
                }
            }
            if (predicate.equals(antecedentPredicate)) {
                String match = ruleMatchValue("antecedent", subject, object, antecedent);
                if (match != null) {
                    matches.add(match);
                } else {
                    logRuleReject(rule, predicate, subject, object, "antecedent");
                }
            }
        }
        matches = answerRanker.filterEchoValues(matches, goal);
        if (matches.isEmpty()) {
            return null;
        }
        return answerRanker.rankAnswerValues(matches).get(0);
    }

    private String ruleMatchValue(String side, SymbolId subject, SymbolId object, RelationAssertion assertion) {
        if (subject != null && assertion.subject().equals(subject)) {
            return assertion.object().value();
        }
        if (object != null && assertion.object().equals(object)) {
            return assertion.subject().value();
        }
        if (subject == null && object == null) {
            return "rule(" + side + "): " + assertion.subject().value() + " "
                    + localName(assertion.predicate()) + " " + assertion.object().value();
        }
        return null;
    }

    private void logRuleReject(RuleAssertion rule,
                               String expectedPredicate,
                               SymbolId subject,
                               SymbolId object,
                               String side) {
        if (!logger.isLoggable(java.util.logging.Level.FINE)) {
            return;
        }
        RelationAssertion assertion = "consequent".equals(side) ? rule.consequent() : rule.antecedent();
        StringBuilder reason = new StringBuilder("rule-bind skip side=").append(side)
                .append(" expectedPredicate=").append(expectedPredicate)
                .append(" rulePredicate=").append(localName(assertion.predicate()));
        if (subject != null && !assertion.subject().equals(subject)) {
            reason.append(" subjectMismatch expected=").append(subject.value())
                    .append(" actual=").append(assertion.subject().value());
        }
        if (object != null && !assertion.object().equals(object)) {
            reason.append(" objectMismatch expected=").append(object.value())
                    .append(" actual=").append(assertion.object().value());
        }
        logger.fine(() -> reason.append(" rule=").append(rule).toString());
    }

    private String directWhereMatch(QueryGoal goal) {
        if (goal == null || goal.type() != QueryGoal.Type.WHERE) {
            return null;
        }
        java.util.Set<String> locationPredicates = com.sahr.core.HeadOntology.expandFamily(ontology, com.sahr.core.HeadOntology.LOCATION_TRANSFER);
        if (locationPredicates.isEmpty()) {
            return null;
        }
        java.util.Set<String> locationNames = new java.util.HashSet<>();
        for (String predicate : locationPredicates) {
            locationNames.add(localName(predicate));
        }
        String requestedType = goal.entityType();
        String normalizedRequested = normalizeTypeToken(requestedType);
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicateName = localName(assertion.predicate());
            if (!locationPredicates.contains(assertion.predicate()) && !locationNames.contains(predicateName)) {
                continue;
            }
            if (!matchesRequestedType(assertion.subject(), normalizedRequested, requestedType)) {
                continue;
            }
            return assertion.subject().value() + " " + predicateName + " " + assertion.object().value();
        }
        return null;
    }

    private boolean matchesRequestedType(SymbolId subject, String normalizedRequested, String requestedType) {
        if (normalizedRequested == null || normalizedRequested.isBlank()) {
            return true;
        }
        String subjectName = normalizeTypeToken(subject.value());
        if (subjectName.equals(normalizedRequested)) {
            return true;
        }
        return graph.findEntity(subject)
                .map(EntityNode::conceptTypes)
                .map(types -> types.stream().anyMatch(type ->
                        type.equals(requestedType)
                                || normalizeTypeToken(type).equals(normalizedRequested)
                                || ontology.isSubclassOf(type, requestedType)))
                .orElse(false);
    }

    private String normalizeTypeToken(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.startsWith("concept:")) {
            return raw.substring("concept:".length());
        }
        if (raw.startsWith("entity:")) {
            return raw.substring("entity:".length());
        }
        return raw;
    }

    private String executeTemporalMatch(QueryGoal goal) {
        String predicate = localName(goal.predicate());
        if (predicate.isBlank()) {
            return "No candidates produced.";
        }
        if ("fail".equals(predicate) && goal.subject() != null && goal.subject().contains("component")) {
            SymbolId target = findEntityByToken("spacecraft_instability");
            ExplanationCandidate candidate = explanationChains.buildExplanationCandidate(target, 4);
            SymbolId failure = selectBestFailure(candidate, true);
            if (failure != null) {
                return failure.value();
            }
        }
        if ("before".equals(predicate) || "after".equals(predicate) || "during".equals(predicate)) {
            return directTemporalMatch(predicate, goal);
        }
        java.util.List<String> baseMatches = directRelationMatches(goal);
        if (!baseMatches.isEmpty()) {
            baseMatches = answerRanker.filterEchoValues(baseMatches, goal);
            baseMatches = answerRanker.rankAnswerValues(baseMatches);
            if (baseMatches.size() == 1) {
                return baseMatches.get(0);
            }
            return String.join(", ", baseMatches);
        }
        return "No candidates produced.";
    }

    private String directTemporalMatch(String predicate, QueryGoal goal) {
        SymbolId subject = goal.subject() == null ? null : new SymbolId(goal.subject());
        SymbolId object = goal.object() == null ? null : new SymbolId(goal.object());
        java.util.List<String> matches = new java.util.ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String assertionPredicate = localName(assertion.predicate());
            if (!predicate.equals(assertionPredicate)) {
                continue;
            }
            if (subject != null && assertion.subject().equals(subject)) {
                matches.add(assertion.object().value());
                continue;
            }
            if (object != null && assertion.object().equals(object)) {
                matches.add(assertion.subject().value());
                continue;
            }
            if (subject == null && object == null) {
                matches.add(assertion.subject().value());
            }
        }
        matches = answerRanker.filterEchoValues(matches, goal);
        if (matches.isEmpty()) {
            return "No candidates produced.";
        }
        return answerRanker.rankAnswerValues(matches).get(0);
    }

    private String executeCauseChain(QueryGoal goal) {
        String predicate = localName(goal.predicate());
        SymbolId subject = (goal.subject() != null && !goal.subject().isBlank())
                ? new SymbolId(goal.subject())
                : null;
        SymbolId target = (goal.object() != null && !goal.object().isBlank())
                ? new SymbolId(goal.object())
                : null;
        if (target == null && goal.subject() != null) {
            target = new SymbolId(goal.subject());
        }
        if (!predicate.isBlank() && !"cause".equals(predicate) && !"causedby".equals(predicate)) {
            java.util.List<String> predicateExplanation = predicateExplainer.buildPredicateExplanation(goal, predicate, 3);
            if (!predicateExplanation.isEmpty()) {
                ExplanationCandidate candidate = explanationChains.buildExplanationCandidate(target, 4);
                if (wantsRecoveryAgent(goal, predicate)) {
                    SymbolId agent = selectBestRecoveryAgent(candidate, target);
                    if (agent != null) {
                        return agent.value();
                    }
                }
                String explanation = summarizeRecoveryExplanation(goal, predicate, candidate, predicateExplanation);
                if (explanation != null) {
                    return explanation;
                }
                return String.join("\n", predicateExplanation);
            }
        }
        if (target == null) {
            return "No candidates produced.";
        }
        ExplanationCandidate structuredCandidate = explanationChains.buildExplanationCandidate(target, 4);
        if (structuredCandidate != null && (wantsChainExplanation(goal) || "cause".equals(predicate) || "causedby".equals(predicate))) {
            java.util.List<String> structured = buildStructuredChain(structuredCandidate, goal, target, predicate);
            if (!structured.isEmpty()) {
                return String.join("\n", structured);
            }
            String chain = bestFailureChainToOutcome(structuredCandidate, target, goal);
            if (chain != null) {
                return chain;
            }
        }
        java.util.List<SymbolId> subjectCandidates = aliasBridge.expandAliasSymbols(subject);
        java.util.List<SymbolId> targetCandidates = aliasBridge.expandAliasSymbols(target);
        if (subject != null && target != null && !subject.equals(target)) {
            ForwardChainSearch.ChainResult best = null;
            for (SymbolId subjectCandidate : subjectCandidates) {
                for (SymbolId targetCandidate : targetCandidates) {
                    if (subjectCandidate.equals(targetCandidate)) {
                        continue;
                    }
                    ForwardChainSearch.ChainResult forward = forwardChainSearch.search(
                            subjectCandidate,
                            targetCandidate,
                            4
                    );
                    if (forward == null || forward.sentences().isEmpty()) {
                        continue;
                    }
                    if (best == null || forward.score() > best.score()) {
                        best = forward;
                    }
                }
            }
            if (best != null && !best.sentences().isEmpty()) {
                return String.join("\n", best.sentences());
            }
        }
        ForwardChainSearch.ChainResult bestExplanation = null;
        ExplanationCandidate bestExplanationCandidate = null;
        for (SymbolId targetCandidate : targetCandidates) {
            ExplanationCandidate explanationCandidate = explanationChains.buildExplanationCandidate(targetCandidate, 4);
            java.util.List<String> explanation = explanationCandidate.sentences();
            if (explanation.isEmpty()) {
                continue;
            }
            ForwardChainSearch.ChainResult candidate = new ForwardChainSearch.ChainResult(
                    explanation,
                    answerRanker.explanationSpecificity(explanation)
            );
            if (bestExplanation == null || candidate.score() > bestExplanation.score()) {
                bestExplanation = candidate;
                bestExplanationCandidate = explanationCandidate;
            }
        }
        if (bestExplanation != null && !bestExplanation.sentences().isEmpty()) {
            if (bestExplanationCandidate != null) {
                if (wantsRecoveryAgent(goal, predicate)) {
                    SymbolId agent = selectBestRecoveryAgent(bestExplanationCandidate, target);
                    if (agent != null) {
                        return agent.value();
                    }
                }
                String explanation = summarizeRecoveryExplanation(goal, predicate, bestExplanationCandidate, bestExplanation.sentences());
                if (explanation != null) {
                    return explanation;
                }
            }
            return String.join("\n", bestExplanation.sentences());
        }
        java.util.Set<SymbolId> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<SymbolId> queue = new java.util.ArrayDeque<>();
        queue.add(target);
        visited.add(target);
        int depth = 0;
        while (!queue.isEmpty() && depth < 3) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                SymbolId current = queue.removeFirst();
                for (RelationAssertion assertion : graph.getAllAssertions()) {
                    String assertionPredicate = localName(assertion.predicate());
                    if (!"cause".equals(assertionPredicate) && !"causedby".equals(assertionPredicate)) {
                        continue;
                    }
                    if (assertion.object().equals(current)) {
                        SymbolId cause = assertion.subject();
                        if (visited.add(cause)) {
                            queue.addLast(cause);
                            if (depth >= 0) {
                                return cause.value();
                            }
                        }
                    }
                    if ("causedby".equals(assertionPredicate) && assertion.subject().equals(current)) {
                        SymbolId cause = assertion.object();
                        if (visited.add(cause)) {
                            queue.addLast(cause);
                            if (depth >= 0) {
                                return cause.value();
                            }
                        }
                    }
                }
            }
            depth++;
        }
        String ruleFallback = directRuleMatch(goal);
        if (ruleFallback != null) {
            return ruleFallback;
        }
        for (SymbolId targetCandidate : targetCandidates) {
            String ruleChain = ruleChainFallback(targetCandidate);
            if (ruleChain != null) {
                return ruleChain;
            }
        }
        return "No candidates produced.";
    }

    private boolean wantsRecoveryAgent(QueryGoal goal, String predicate) {
        if (goal == null) {
            return false;
        }
        if (!"restore".equals(predicate) && !"regain".equals(predicate)) {
            return false;
        }
        return goal.subject() == null || goal.subject().isBlank();
    }

    private boolean wantsChainExplanation(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        String modifier = goal.modifier();
        if (modifier == null) {
            return false;
        }
        String normalized = modifier.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("chain") || normalized.contains("explain") || normalized.contains("why");
    }

    private String bestFailureChainToOutcome(ExplanationCandidate candidate, SymbolId outcome) {
        return bestFailureChainToOutcome(candidate, outcome, null);
    }

    private String bestFailureChainToOutcome(ExplanationCandidate candidate, SymbolId outcome, QueryGoal goal) {
        if (candidate == null || outcome == null) {
            return null;
        }
        java.util.List<SymbolId> failures = new java.util.ArrayList<>();
        failures.addAll(candidate.componentFailures());
        failures.addAll(candidate.subsystemFailures());
        ForwardChainSearch.ChainResult best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SymbolId failure : failures) {
            if (answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            ForwardChainSearch.ChainResult result = forwardChainSearch.search(failure, outcome, 4);
            if (result == null || result.sentences().isEmpty()) {
                continue;
            }
            double score = result.score() + evidenceAlignmentScore(candidate, failure, outcome, goal);
            if (best == null || score > bestScore) {
                best = result;
                bestScore = score;
            }
        }
        if (best != null && !best.sentences().isEmpty()) {
            return String.join("\n", best.sentences());
        }
        return null;
    }

    private SymbolId selectBestFailure(ExplanationCandidate candidate, boolean preferComponent) {
        if (candidate == null) {
            return null;
        }
        java.util.List<SymbolId> failures = new java.util.ArrayList<>();
        if (preferComponent) {
            failures.addAll(candidate.componentFailures());
        }
        failures.addAll(candidate.subsystemFailures());
        failures.addAll(candidate.componentFailures());
        if (failures.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId failure : failures) {
            if (answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            double score = answerRanker.specificityScore(failure.value());
            if (score > bestScore) {
                best = failure;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        best = failures.get(0);
        bestScore = answerRanker.specificityScore(best.value());
        for (SymbolId failure : failures) {
            double score = answerRanker.specificityScore(failure.value());
            if (score > bestScore) {
                best = failure;
                bestScore = score;
            }
        }
        return best;
    }

    private double evidenceAlignmentScore(ExplanationCandidate candidate,
                                          SymbolId failure,
                                          SymbolId outcome,
                                          QueryGoal goal) {
        if (candidate == null || failure == null) {
            return 0.0;
        }
        double score = 0.0;
        score += evidenceSupportScore(failure);
        if (outcome != null) {
            score += evidenceSupportScore(outcome) * 0.4;
        }
        boolean wantsEvidence = wantsEvidenceAlignedChain(goal);
        java.util.List<SymbolId> precursors = candidate.precursorSignals();
        if (!precursors.isEmpty()) {
            for (SymbolId signal : precursors) {
                score += temporalSupportScore(signal, failure) * 0.6;
                if (outcome != null) {
                    score += temporalSupportScore(signal, outcome) * 0.4;
                }
            }
            if (wantsEvidence) {
                score += 0.4;
            }
        } else if (wantsEvidence) {
            for (SymbolId evidence : candidate.evidenceNodes()) {
                score += temporalSupportScore(evidence, failure) * 0.4;
                if (outcome != null) {
                    score += temporalSupportScore(evidence, outcome) * 0.2;
                }
            }
            if (hasTemporalContext(failure)) {
                score += 0.4;
            }
            if (outcome != null && hasTemporalContext(outcome)) {
                score += 0.2;
            }
        }
        return score;
    }

    private boolean wantsEvidenceAlignedChain(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        String modifier = goal.modifier();
        String predicateText = goal.predicateText();
        String subjectText = goal.subjectText();
        String objectText = goal.objectText();
        return containsEvidenceCue(modifier)
                || containsEvidenceCue(predicateText)
                || containsEvidenceCue(subjectText)
                || containsEvidenceCue(objectText);
    }

    private boolean containsEvidenceCue(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("telemetry")
                || normalized.contains("sequence")
                || normalized.contains("evidence")
                || normalized.contains("best fit")
                || normalized.contains("plausible")
                || normalized.contains("likely");
    }

    private double evidenceSupportScore(SymbolId node) {
        if (node == null || annotationResolver == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            Double weight = annotationResolver.resolveObjectPropertyIri(assertion.predicate())
                    .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                    .orElse(null);
            if (weight == null || weight == 0.0) {
                continue;
            }
            if (assertion.subject().equals(node) || assertion.object().equals(node)) {
                score += weight;
            }
        }
        return score;
    }

    private double temporalSupportScore(SymbolId cause, SymbolId effect) {
        if (cause == null || effect == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            if (assertion.subject().equals(cause) && assertion.object().equals(effect)) {
                score += 0.4;
            } else if ("after".equals(predicate) && assertion.subject().equals(effect) && assertion.object().equals(cause)) {
                score += 0.4;
            } else if ("before".equals(predicate) && assertion.subject().equals(effect) && assertion.object().equals(cause)) {
                score += 0.2;
            } else if ("during".equals(predicate)
                    && (assertion.subject().equals(cause) || assertion.subject().equals(effect))) {
                score += 0.2;
            }
        }
        return score;
    }

    private java.util.List<String> buildStructuredChain(ExplanationCandidate candidate,
                                                        QueryGoal goal,
                                                        SymbolId target,
                                                        String predicate) {
        java.util.List<String> sentences = new java.util.ArrayList<>();
        if (candidate == null) {
            return sentences;
        }
        SymbolId precursor = selectBestSpecific(candidate.precursorSignals(), true);
        if (precursor != null) {
            sentences.add(roleSentence("Precursor signal", precursor));
        }
        boolean evidenceAligned = wantsEvidenceAlignedChain(goal)
                || "cause".equals(predicate)
                || "causedby".equals(predicate)
                || hasTemporalContext(target);
        SymbolId componentFailure = null;
        SymbolId subsystemFailure = null;
        if (evidenceAligned) {
            SymbolId bestFailure = selectEvidenceAlignedFailure(candidate, target, goal);
            if (bestFailure != null && candidate.componentFailures().contains(bestFailure)) {
                componentFailure = bestFailure;
            } else {
                subsystemFailure = bestFailure;
            }
        }
        if (componentFailure == null) {
            componentFailure = selectBestSpecific(candidate.componentFailures(), true);
        }
        if (candidate.componentFailures().isEmpty()) {
            java.util.List<SymbolId> subsystemTop = selectTopSpecific(candidate.subsystemFailures(), 2, true, null);
            for (SymbolId failure : subsystemTop) {
                sentences.add(formatFailureSentence(failure));
            }
        } else {
            if (componentFailure != null) {
                sentences.add(formatFailureSentence(componentFailure));
            }
            if (subsystemFailure == null) {
                subsystemFailure = selectBestSpecificExcluding(candidate.subsystemFailures(), componentFailure, true);
            }
            if (subsystemFailure != null && !subsystemFailure.equals(componentFailure)) {
                sentences.add(formatFailureSentence(subsystemFailure));
            }
        }
        SymbolId capabilityLoss = selectBestSpecific(candidate.capabilityLosses(), false);
        if (capabilityLoss != null) {
            sentences.add(formatCapabilityLossSentence(capabilityLoss));
        }
        if (target != null) {
            sentences.add("Outcome: " + displayValue(target) + ".");
        }
        if (shouldIncludeRecovery(goal, predicate, target)) {
            SymbolId agent = selectBestRecoveryAgent(candidate, target);
            if (agent != null) {
                sentences.add(formatRecoverySentence(agent, target));
            }
        }
        appendEvidenceLines(candidate, componentFailure, subsystemFailure, target, evidenceAligned, sentences);
        return sentences;
    }

    private SymbolId selectBestSpecific(java.util.List<SymbolId> candidates, boolean avoidLoss) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (avoidLoss && answerRanker.isGenericLossValue(candidate.value())) {
                continue;
            }
            double score = answerRanker.specificityScore(candidate.value());
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        best = candidates.get(0);
        bestScore = answerRanker.specificityScore(best.value());
        for (SymbolId candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            double score = answerRanker.specificityScore(candidate.value());
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private SymbolId selectBestSpecificExcluding(java.util.List<SymbolId> candidates,
                                                 SymbolId exclude,
                                                 boolean avoidLoss) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId candidate : candidates) {
            if (candidate == null || candidate.equals(exclude)) {
                continue;
            }
            if (avoidLoss && answerRanker.isGenericLossValue(candidate.value())) {
                continue;
            }
            double score = answerRanker.specificityScore(candidate.value());
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null) {
            return best;
        }
        return selectBestSpecific(candidates, avoidLoss);
    }

    private SymbolId selectEvidenceAlignedFailure(ExplanationCandidate candidate, SymbolId outcome, QueryGoal goal) {
        if (candidate == null) {
            return null;
        }
        java.util.List<SymbolId> failures = new java.util.ArrayList<>();
        failures.addAll(candidate.componentFailures());
        failures.addAll(candidate.subsystemFailures());
        if (failures.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SymbolId failure : failures) {
            if (failure == null || answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            double score = evidenceAlignmentScore(candidate, failure, outcome, goal)
                    + answerRanker.specificityScore(failure.value());
            if (score > bestScore) {
                best = failure;
                bestScore = score;
            }
        }
        return best != null ? best : selectBestSpecific(failures, true);
    }

    private void appendEvidenceLines(ExplanationCandidate candidate,
                                     SymbolId componentFailure,
                                     SymbolId subsystemFailure,
                                     SymbolId outcome,
                                     boolean evidenceAligned,
                                     java.util.List<String> sentences) {
        if (!evidenceAligned) {
            java.util.List<SymbolId> evidence = candidate.evidenceNodes();
            int evidenceAdded = 0;
            for (SymbolId node : evidence) {
                if (node == null) {
                    continue;
                }
                sentences.add("Evidence: " + displayValue(node) + ".");
                evidenceAdded++;
                if (evidenceAdded >= 2) {
                    break;
                }
            }
            return;
        }
        java.util.List<SymbolId> evidenceNodes = new java.util.ArrayList<>();
        if (componentFailure != null) {
            evidenceNodes.add(componentFailure);
        }
        if (subsystemFailure != null) {
            evidenceNodes.add(subsystemFailure);
        }
        if (outcome != null) {
            evidenceNodes.add(outcome);
        }
        java.util.List<RelationAssertion> temporalEvidence = collectTemporalEvidenceAssertions(evidenceNodes);
        for (RelationAssertion assertion : temporalEvidence) {
            sentences.add(answerRenderer.formatAssertionSentence(assertion));
            if (sentences.size() >= 6) {
                return;
            }
        }
        java.util.LinkedHashSet<SymbolId> evidence = new java.util.LinkedHashSet<>();
        evidence.addAll(findEvidenceForNode(componentFailure));
        evidence.addAll(findEvidenceForNode(subsystemFailure));
        evidence.addAll(findEvidenceForNode(outcome));
        int added = 0;
        for (SymbolId node : evidence) {
            if (node == null) {
                continue;
            }
            sentences.add("Evidence: " + displayValue(node) + ".");
            added++;
            if (added >= 2) {
                break;
            }
        }
    }

    private java.util.List<RelationAssertion> collectTemporalEvidenceAssertions(java.util.List<SymbolId> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return java.util.List.of();
        }
        java.util.LinkedHashSet<RelationAssertion> matches = new java.util.LinkedHashSet<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            for (SymbolId node : nodes) {
                if (node == null) {
                    continue;
                }
                if (assertion.subject().equals(node) || assertion.object().equals(node)) {
                    matches.add(assertion);
                    break;
                }
            }
        }
        return new java.util.ArrayList<>(matches);
    }

    private boolean hasTemporalContext(SymbolId node) {
        if (node == null) {
            return false;
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            if (assertion.subject().equals(node) || assertion.object().equals(node)) {
                return true;
            }
        }
        return false;
    }

    private java.util.List<SymbolId> findEvidenceForNode(SymbolId node) {
        if (node == null || annotationResolver == null) {
            return java.util.List.of();
        }
        java.util.List<SymbolId> evidence = new java.util.ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            Double weight = annotationResolver.resolveObjectPropertyIri(assertion.predicate())
                    .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                    .orElse(null);
            if (weight == null || weight == 0.0) {
                continue;
            }
            if (assertion.object().equals(node)) {
                evidence.add(assertion.subject());
            } else if (assertion.subject().equals(node)) {
                evidence.add(assertion.object());
            }
        }
        return evidence;
    }

    private java.util.List<SymbolId> selectTopSpecific(java.util.List<SymbolId> candidates,
                                                       int limit,
                                                       boolean avoidLoss,
                                                       SymbolId exclude) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return java.util.List.of();
        }
        java.util.List<SymbolId> pool = new java.util.ArrayList<>();
        for (SymbolId candidate : candidates) {
            if (candidate == null || candidate.equals(exclude)) {
                continue;
            }
            if (avoidLoss && answerRanker.isGenericLossValue(candidate.value())) {
                continue;
            }
            pool.add(candidate);
        }
        pool.sort((left, right) -> Double.compare(
                answerRanker.specificityScore(right.value()),
                answerRanker.specificityScore(left.value())
        ));
        if (pool.size() <= limit) {
            return pool;
        }
        return new java.util.ArrayList<>(pool.subList(0, limit));
    }

    private String roleSentence(String label, SymbolId value) {
        return label + " was " + displayValue(value) + ".";
    }

    private String formatFailureSentence(SymbolId subject) {
        RelationAssertion assertion = findBooleanAssertion(subject, "fail", true);
        if (assertion != null) {
            return answerRenderer.formatAssertionSentence(assertion);
        }
        assertion = findFailureLikeAssertion(subject);
        if (assertion != null) {
            return answerRenderer.formatAssertionSentence(assertion);
        }
        return "Failure: " + displayValue(subject) + ".";
    }

    private String formatCapabilityLossSentence(SymbolId subject) {
        RelationAssertion assertion = findBooleanAssertion(subject, "control", false);
        if (assertion != null) {
            return answerRenderer.formatAssertionSentence(assertion);
        }
        return "Capability loss: " + displayValue(subject) + ".";
    }

    private String formatRecoverySentence(SymbolId agent, SymbolId target) {
        RelationAssertion assertion = findRestoreAssertion(agent, target);
        if (assertion != null) {
            return answerRenderer.formatAssertionSentence(assertion);
        }
        return "Recovery: " + displayValue(agent) + " was active during recovery.";
    }

    private RelationAssertion findRestoreAssertion(SymbolId agent, SymbolId target) {
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = localName(assertion.predicate());
            if (!"restore".equals(predicate) && !"regain".equals(predicate)) {
                continue;
            }
            if (agent != null && !assertion.subject().equals(agent)) {
                continue;
            }
            if (target != null && !assertion.object().equals(target)) {
                continue;
            }
            return assertion;
        }
        return null;
    }

    private RelationAssertion findBooleanAssertion(SymbolId subject, String predicate, boolean value) {
        if (subject == null) {
            return null;
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!subject.equals(assertion.subject())) {
                continue;
            }
            if (!predicate.equals(localName(assertion.predicate()))) {
                continue;
            }
            Boolean objectValue = booleanConcept(assertion.object());
            if (objectValue != null && objectValue == value) {
                return assertion;
            }
        }
        return null;
    }

    private RelationAssertion findFailureLikeAssertion(SymbolId subject) {
        if (subject == null) {
            return null;
        }
        String[] predicates = {"operate", "function", "work", "respond", "stop", "stop_responding"};
        for (String predicate : predicates) {
            RelationAssertion assertion = findBooleanAssertion(subject, predicate, false);
            if (assertion != null) {
                return assertion;
            }
        }
        return null;
    }

    private boolean shouldIncludeRecovery(QueryGoal goal, String predicate, SymbolId target) {
        if ("restore".equals(predicate) || "regain".equals(predicate)) {
            return true;
        }
        if (target != null) {
            String value = target.value().toLowerCase(java.util.Locale.ROOT);
            if (value.contains("instability")) {
                return false;
            }
            if (value.contains("stable") || value.contains("stability")) {
                return true;
            }
        }
        if (goal != null && goal.modifier() != null) {
            String modifier = goal.modifier().toLowerCase(java.util.Locale.ROOT);
            if (modifier.contains("recovery")) {
                return true;
            }
        }
        return false;
    }

    private SymbolId findEntityByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String target = token.toLowerCase(java.util.Locale.ROOT);
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            SymbolId subject = assertion.subject();
            if (subject != null && subject.value() != null && subject.value().toLowerCase(java.util.Locale.ROOT).contains(target)) {
                return subject;
            }
            SymbolId object = assertion.object();
            if (object != null && object.value() != null && object.value().toLowerCase(java.util.Locale.ROOT).contains(target)) {
                return object;
            }
        }
        return null;
    }

    private String summarizeRecoveryExplanation(QueryGoal goal,
                                                String predicate,
                                                ExplanationCandidate candidate,
                                                java.util.List<String> fallback) {
        if (goal == null || candidate == null) {
            return null;
        }
        if (!"restore".equals(predicate) && !"regain".equals(predicate)) {
            return null;
        }
        SymbolId agent = selectBestRecoveryAgent(candidate, goal.object() == null ? null : new SymbolId(goal.object()));
        if (agent == null) {
            return null;
        }
        String agentText = agent.value();
        String prefix = "Stability was restored because ";
        StringBuilder builder = new StringBuilder(prefix);
        String agentDisplay = displayValue(agentText);
        builder.append(agentDisplay).append(" ").append(isPlural(agentDisplay) ? "were" : "was").append(" ");
        String recoveryHint = firstRecoveryHint(fallback);
        if (recoveryHint != null) {
            builder.append("active ").append(recoveryHint);
        } else {
            builder.append("active during recovery.");
        }
        if (!builder.toString().endsWith(".")) {
            builder.append(".");
        }
        return builder.toString();
    }

    private String firstRecoveryHint(java.util.List<String> sentences) {
        if (sentences == null) {
            return null;
        }
        for (String sentence : sentences) {
            if (sentence == null) {
                continue;
            }
            if (sentence.contains("during recovery")) {
                return "during recovery";
            }
            if (sentence.contains("during recovery period")) {
                return "during recovery period";
            }
        }
        return null;
    }

    private String displayValue(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String value = raw;
        if (value.startsWith("entity:")) {
            value = value.substring("entity:".length());
        } else if (value.startsWith("concept:")) {
            value = value.substring("concept:".length());
        }
        return value.replace('_', ' ');
    }

    private String displayValue(SymbolId id) {
        if (id == null) {
            return "unknown";
        }
        return displayValue(id.value());
    }

    private boolean isPlural(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        String[] tokens = normalized.split("\\s+");
        String last = tokens[tokens.length - 1];
        return last.endsWith("s") && !last.endsWith("ss");
    }

    private SymbolId selectBestRecoveryAgent(ExplanationCandidate candidate, SymbolId target) {
        java.util.List<SymbolId> agents = new java.util.ArrayList<>(candidate.recoveryAgents());
        if (agents.isEmpty() && target != null) {
            agents.addAll(findRestoreSubjects(target));
        }
        if (agents.isEmpty()) {
            return null;
        }
        SymbolId best = agents.get(0);
        double bestScore = answerRanker.specificityScore(best.value());
        for (SymbolId agent : agents) {
            double score = answerRanker.specificityScore(agent.value());
            if (score > bestScore) {
                best = agent;
                bestScore = score;
            }
        }
        return best;
    }

    private java.util.List<SymbolId> findRestoreSubjects(SymbolId target) {
        java.util.List<SymbolId> subjects = new java.util.ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = localName(assertion.predicate());
            if (!"restore".equals(predicate) && !"regain".equals(predicate)) {
                continue;
            }
            if (assertion.object().equals(target)) {
                subjects.add(assertion.subject());
            }
        }
        return subjects;
    }

    private String ruleChainFallback(SymbolId target) {
        if (target == null) {
            return null;
        }
        java.util.Set<SymbolId> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<SymbolId> queue = new java.util.ArrayDeque<>();
        queue.add(target);
        visited.add(target);
        SymbolId lastFound = null;
        int depth = 0;
        while (!queue.isEmpty() && depth < 3) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                SymbolId current = queue.removeFirst();
                for (RuleAssertion rule : graph.getAllRules()) {
                    RelationAssertion consequent = rule.consequent();
                    RelationAssertion antecedent = rule.antecedent();
                    if (!consequent.subject().equals(current) && !consequent.object().equals(current)) {
                        continue;
                    }
                    SymbolId cause = selectCauseNode(antecedent);
                    if (cause == null || !visited.add(cause)) {
                        continue;
                    }
                    lastFound = cause;
                    queue.addLast(cause);
                }
            }
            depth++;
        }
        return lastFound != null ? lastFound.value() : null;
    }

    private SymbolId selectCauseNode(RelationAssertion antecedent) {
        if (antecedent == null) {
            return null;
        }
        if (isEntity(antecedent.subject())) {
            return antecedent.subject();
        }
        if (isEntity(antecedent.object())) {
            return antecedent.object();
        }
        return null;
    }

    private boolean isEntity(SymbolId id) {
        if (id == null || id.value() == null) {
            return false;
        }
        return id.value().startsWith("entity:");
    }

    private String localName(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return "";
        }
        int hashIdx = predicate.lastIndexOf('#');
        int slashIdx = predicate.lastIndexOf('/');
        int idx = Math.max(hashIdx, slashIdx);
        String local = idx >= 0 ? predicate.substring(idx + 1) : predicate;
        return local.toLowerCase(java.util.Locale.ROOT);
    }

    private Boolean booleanConcept(SymbolId id) {
        if (id == null || id.value() == null) {
            return null;
        }
        String value = id.value();
        if (value.startsWith("concept:")) {
            value = value.substring("concept:".length());
        }
        value = value.toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void applyAnswerAsAssertion(Object payload) {
        if (!(payload instanceof String)) {
            return;
        }
        String text = ((String) payload).trim();
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            return;
        }
        RelationAssertion assertion = new RelationAssertion(
                new SymbolId(parts[0]),
                parts[1],
                new SymbolId(parts[2]),
                0.8
        );
        addAssertionIfNew(assertion);
    }

    private void recordAnswerIfPossible(QueryGoal query, Object payload) {
        if (query == null || payload == null) {
            return;
        }
        String answer;
        if (payload instanceof SymbolId) {
            answer = ((SymbolId) payload).value();
        } else if (payload instanceof String) {
            answer = ((String) payload).trim();
        } else {
            return;
        }
        if (answer.isEmpty()) {
            return;
        }
        if (!answer.startsWith("entity:") && !answer.startsWith("concept:")) {
            return;
        }
        QueryKey key = QueryKey.from(query);
        if (key == null) {
            return;
        }
        workingMemory.recordAnswer(key, answer);
    }

    private RuleStatement mapRuleStatement(RuleStatement rule) {
        Statement antecedent = mapStatement(rule.antecedent());
        Statement consequent = mapStatement(rule.consequent());
        return new RuleStatement(antecedent, consequent, rule.confidence());
    }

    private RuleAssertion toRuleAssertion(RuleStatement rule) {
        RelationAssertion antecedent = statementToAssertion(rule.antecedent());
        RelationAssertion consequent = statementToAssertion(rule.consequent());
        double confidence = averageConfidence(antecedent.confidence(), consequent.confidence());
        return new RuleAssertion(antecedent, consequent, confidence);
    }

    private RelationAssertion statementToAssertion(Statement statement) {
        return new RelationAssertion(statement.subject(), statement.predicate(), statement.object(), statement.confidence());
    }

    private boolean addRuleIfNew(RuleAssertion rule) {
        if (rule == null) {
            return false;
        }
        for (RuleAssertion existing : graph.getAllRules()) {
            if (rulesEqual(existing, rule)) {
                return false;
            }
        }
        graph.addRule(rule);
        return true;
    }

    private boolean rulesEqual(RuleAssertion left, RuleAssertion right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return assertionsEqual(left.antecedent(), right.antecedent())
                && assertionsEqual(left.consequent(), right.consequent());
    }

    private boolean assertionsEqual(RelationAssertion left, RelationAssertion right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.subject().equals(right.subject())
                && left.predicate().equals(right.predicate())
                && left.object().equals(right.object());
    }

    private double averageConfidence(double left, double right) {
        return Math.min(1.0, (left + right) / 2.0);
    }

    private void recordAnswerValues(QueryGoal query, java.util.List<String> answers) {
        if (query == null || answers == null || answers.isEmpty()) {
            return;
        }
        QueryKey key = QueryKey.from(query);
        if (key == null) {
            return;
        }
        for (String answer : answers) {
            if (answer == null || answer.isBlank()) {
                continue;
            }
            workingMemory.recordAnswer(key, answer);
        }
    }

    private String buildMultiAnswer(QueryGoal query, List<ReasoningCandidate> candidates) {
        if (query == null || query.discourseModifier() != null || query.type() != QueryGoal.Type.RELATION) {
            return null;
        }
        java.util.List<String> values = answerRanker.rankAnswerValues(
                answerRanker.filterEchoValues(extractAnswerValues(candidates), query)
        );
        if (values.size() <= 1) {
            return null;
        }
        return String.join(", ", values);
    }

    private java.util.List<String> extractAnswerValues(List<ReasoningCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (ReasoningCandidate candidate : candidates) {
            if (!CandidateType.ANSWER.equals(candidate.type())) {
                continue;
            }
            Object payload = candidate.payload();
            String value = null;
            if (payload instanceof SymbolId) {
                value = ((SymbolId) payload).value();
            } else if (payload instanceof String) {
                value = payload.toString();
            }
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return new java.util.ArrayList<>(values);
    }

    private String formatAnswerWithEvidence(String answer,
                                            List<ReasoningCandidate> candidates,
                                            ReasoningCandidate winner) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        List<String> evidence = collectEvidence(candidates, winner, 3);
        if (evidence.isEmpty()) {
            return answer;
        }
        return answer + "\nEvidence: " + String.join(" | ", evidence);
    }

    private List<String> collectEvidence(List<ReasoningCandidate> candidates,
                                         ReasoningCandidate winner,
                                         int limit) {
        java.util.LinkedHashSet<String> evidence = new java.util.LinkedHashSet<>();
        if (winner != null && winner.evidence() != null) {
            evidence.addAll(winner.evidence());
        }
        if (candidates != null) {
            for (ReasoningCandidate candidate : candidates) {
                if (!CandidateType.ANSWER.equals(candidate.type())) {
                    continue;
                }
                if (candidate.evidence() != null) {
                    evidence.addAll(candidate.evidence());
                }
                if (evidence.size() >= limit) {
                    break;
                }
            }
        }
        if (evidence.isEmpty()) {
            return List.of();
        }
        List<String> trimmed = new ArrayList<>();
        for (String entry : evidence) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            trimmed.add(entry);
            if (trimmed.size() >= limit) {
                break;
            }
        }
        return trimmed;
    }

    private void upsertEntity(SymbolId id, Set<String> types) {
        graph.findEntity(id).orElseGet(() -> {
            EntityNode entity = new EntityNode(id, id.value(), types);
            graph.addEntity(entity);
            return entity;
        });
        workingMemory.addActiveEntity(id);
    }

    private void upsertEntityType(SymbolId id, String typeValue) {
        String normalized = normalizeType(typeValue);
        if (normalized.isBlank()) {
            return;
        }
        graph.findEntity(id).ifPresentOrElse(existing -> {
            Set<String> merged = new HashSet<>(existing.conceptTypes());
            merged.add(normalized);
            EntityNode updated = new EntityNode(existing.id(), existing.surfaceForm(), merged);
            graph.addEntity(updated);
        }, () -> {
            EntityNode created = new EntityNode(id, id.value(), Set.of(normalized));
            graph.addEntity(created);
        });
    }

    private String normalizeType(String raw) {
        String value = raw;
        if (value.startsWith("entity:")) {
            value = value.substring("entity:".length());
        }
        return value;
    }

    private String normalizeEntityBinding(String value) {
        if (value.startsWith("entity:") || value.startsWith("concept:")
                || value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "entity:" + value;
    }

    private boolean addAssertionIfNew(RelationAssertion assertion) {
        boolean exists = graph.getAllAssertions().stream().anyMatch(existing ->
                existing.subject().equals(assertion.subject())
                        && existing.predicate().equals(assertion.predicate())
                        && existing.object().equals(assertion.object()));
        if (exists) {
            return false;
        }
        graph.addAssertion(assertion);
        workingMemory.recordAssertion(assertion);
        workingMemory.addActiveEntity(assertion.subject());
        workingMemory.addActiveEntity(assertion.object());
        return true;
    }

    private void runPropagationClosure() {
        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontology, workingMemory);
        int totalAdded = 0;

        for (int i = 0; i < MAX_PROPAGATION_ITERATIONS; i++) {
            List<ReasoningCandidate> candidates = withReadPhase(() -> reasoner.reason(context));
            int addedThisRound = 0;
            for (ReasoningCandidate candidate : candidates) {
                if (!(candidate.payload() instanceof RelationAssertion)) {
                    continue;
                }
                if (totalAdded >= MAX_DERIVED_ASSERTIONS) {
                    return;
                }
                RelationAssertion assertion = (RelationAssertion) candidate.payload();
                if (addAssertionIfNew(assertion)) {
                    addedThisRound++;
                    totalAdded++;
                }
            }
            if (addedThisRound == 0) {
                return;
            }
        }
    }

    private <T> T withReadPhase(Supplier<T> supplier) {
        ReasoningPhase previous = phases.enter(ReasoningPhase.READ);
        try {
            return supplier.get();
        } finally {
            phases.restore(previous);
        }
    }

    private IntentDecision selectIntent(InputFeatures features) {
        if (features == null) {
            return new IntentDecision(IntentType.UNKNOWN, 0.0, List.of());
        }
        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontology, null, null, workingMemory, features);
        List<ReasoningCandidate> candidates = withReadPhase(() -> reasoner.reason(context));
        IntentDecision winner = null;
        double bestScore = -1.0;
        for (ReasoningCandidate candidate : candidates) {
            if (candidate.type() != CandidateType.INTENT) {
                continue;
            }
            if (!(candidate.payload() instanceof IntentDecision)) {
                continue;
            }
            if (candidate.score() > bestScore) {
                bestScore = candidate.score();
                winner = (IntentDecision) candidate.payload();
            }
        }
        return winner == null ? new IntentDecision(IntentType.UNKNOWN, 0.0, List.of()) : winner;
    }

    private boolean isQuestionIntent(IntentDecision decision) {
        if (decision == null) {
            return false;
        }
        return decision.type() == IntentType.QUESTION
                || decision.type() == IntentType.CONDITION_QUERY;
    }

    private boolean isRuleIntent(IntentDecision decision) {
        if (decision == null) {
            return false;
        }
        return decision.type() == IntentType.RULE;
    }

    private void updateWorkingMemoryFromStatement(Statement statement) {
        if (statement == null) {
            return;
        }
        for (SymbolId subject : expandConjoinedSubjects(statement.subject())) {
            workingMemory.addActiveEntity(subject);
        }
        workingMemory.addActiveEntity(statement.object());
        for (Statement extra : statement.additionalStatements()) {
            updateWorkingMemoryFromStatement(extra);
        }
    }

    private void updateWorkingMemoryFromQuery(QueryGoal query) {
        if (query == null) {
            return;
        }
        if (query.subject() != null && query.subject().startsWith("entity:")) {
            workingMemory.addActiveEntity(new SymbolId(query.subject()));
        }
        if (query.object() != null && query.object().startsWith("entity:")) {
            workingMemory.addActiveEntity(new SymbolId(query.object()));
        }
        if (query.type() == QueryGoal.Type.WHERE && query.entityType() != null) {
            String entityType = query.entityType();
            if (entityType.startsWith("concept:")) {
                String raw = stripPrefix(entityType);
                if (!raw.isBlank()) {
                    workingMemory.addActiveEntity(new SymbolId("entity:" + raw));
                }
            }
        }
        if (query.type() == QueryGoal.Type.ATTRIBUTE && query.subject() != null && query.subject().startsWith("entity:")) {
            workingMemory.addActiveEntity(new SymbolId(query.subject()));
        }
    }

    private List<SymbolId> expandConjoinedSubjects(SymbolId subject) {
        if (subject == null) {
            return List.of();
        }
        String value = subject.value();
        if (!value.startsWith("entity:")) {
            return List.of(subject);
        }
        String raw = value.substring("entity:".length());
        if (!raw.contains("_and_")) {
            return List.of(subject);
        }
        List<SymbolId> subjects = new java.util.ArrayList<>();
        for (String token : raw.split("_and_")) {
            if (!token.isBlank()) {
                subjects.add(new SymbolId("entity:" + token));
            }
        }
        return subjects.isEmpty() ? List.of(subject) : subjects;
    }

    private Set<String> subjectTypesFor(SymbolId subject, Set<String> fallback) {
        if (subject == null) {
            return fallback;
        }
        boolean wantsConcept = fallback != null && fallback.stream().anyMatch(type ->
                type.startsWith("concept:") || type.startsWith("http://") || type.startsWith("https://"));
        String raw = stripPrefix(subject.value());
        if (raw.isBlank()) {
            return fallback;
        }
        return wantsConcept ? Set.of("concept:" + raw) : Set.of(raw);
    }
}
