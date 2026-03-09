package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.HeadContext;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.ReasoningTrace;
import com.sahr.core.ReasoningTraceEntry;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.core.CandidateType;
import com.sahr.core.WorkingMemory;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.nlp.NoopTermMapper;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.Statement;
import com.sahr.nlp.StatementBatch;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final TermMapper termMapper;
    private final ReasoningTrace trace;
    private final WorkingMemory workingMemory;

    public SahrAgent(
            KnowledgeBase graph,
            OntologyService ontology,
            SahrReasoner reasoner,
            SimpleQueryParser parser
    ) {
        this(graph, ontology, reasoner, parser, new NoopTermMapper());
    }

    public SahrAgent(
            KnowledgeBase graph,
            OntologyService ontology,
            SahrReasoner reasoner,
            SimpleQueryParser parser,
            TermMapper termMapper
    ) {
        this.graph = graph;
        this.ontology = ontology;
        this.reasoner = reasoner;
        this.parser = parser;
        this.statementParser = new StatementParser();
        this.termMapper = termMapper;
        this.trace = new ReasoningTrace();
        this.workingMemory = new WorkingMemory();
    }

    public String handle(String input) {
        Optional<Statement> statement = parser.isQuestion(input)
                ? Optional.empty()
                : statementParser.parse(input).map(this::mapStatement);
        QueryGoal query = mapQuery(parser.parse(input));
        logger.fine(() -> "Input='" + input + "' statementPresent=" + statement.isPresent()
                + " queryIntent=" + query.type());

        statement.ifPresent(this::updateWorkingMemoryFromStatement);
        updateWorkingMemoryFromQuery(query);

        if (!isQuestion(query)) {
            HeadContext context = new HeadContext(query, graph, ontology, statement.orElse(null), workingMemory);
            return handleSingle(context, query);
        }
        return handleWithSubgoals(query, statement.orElse(null));
    }

    public Optional<ReasoningTrace> trace() {
        return Optional.of(trace);
    }

    public void resetWorkingMemory() {
        workingMemory.clear();
    }

    private QueryGoal mapQuery(QueryGoal query) {
        if (query.type() == QueryGoal.Type.UNKNOWN) {
            return query;
        }

        String requestedType = mapEntityType(query.entityType());
        String expectedRange = mapExpectedRange(query.expectedRange());
        String expectedType = mapExpectedType(query.expectedType());
        String subject = mapEntity(query.subject());
        String object = mapEntity(query.object());
        String predicate = mapPredicate(query.predicate());

        QueryGoal mapped = new QueryGoal(
                query.type(),
                subject,
                object,
                predicate,
                expectedType,
                requestedType,
                expectedRange,
                query.subjectText(),
                query.objectText(),
                query.predicateText(),
                query.goalId(),
                query.parentGoalId(),
                query.depth()
        );

        return normalizeQuery(mapped);
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
        Optional<String> mappedType = termMapper.mapToken(expectedType);
        return mappedType.orElse("concept:" + expectedType);
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
        if (statement.objectIsConcept()) {
            Optional<String> mapped = termMapper.mapToken(stripPrefix(statement.object().value()));
            if (mapped.isPresent()) {
                objectId = new SymbolId(mapped.get());
                objectTypes = Set.of(mapped.get());
            }
        }

        String predicate = statement.predicate();
        Optional<String> predicateIri = termMapper.mapPredicateToken(predicate);
        if (predicateIri.isPresent()) {
            predicate = predicateIri.get();
        }

        return new Statement(
                statement.subject(),
                objectId,
                predicate,
                subjectTypes,
                objectTypes,
                statement.objectIsConcept(),
                statement.confidence()
        );
    }

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

    private String applyAssertion(Object payload) {
        if (payload instanceof RelationAssertion) {
            RelationAssertion assertion = (RelationAssertion) payload;
            addAssertionIfNew(assertion);
            if (PREDICATE_TYPE.equals(assertion.predicate())) {
                upsertEntityType(assertion.subject(), assertion.object().value());
            }
            runPropagationClosure();
            logger.fine(() -> "Applied assertion payload: " + payload);
            return "Assertion recorded.";
        }
        if (payload instanceof StatementBatch) {
            StatementBatch batch = (StatementBatch) payload;
            for (Statement statement : batch.statements()) {
                applyAssertion(statement);
            }
            return "Assertion recorded.";
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
            for (SymbolId subject : subjects) {
                RelationAssertion assertion = new RelationAssertion(
                        subject,
                        statement.predicate(),
                        statement.object(),
                        statement.confidence()
                );
                addAssertionIfNew(assertion);
                if (PREDICATE_TYPE.equals(statement.predicate())) {
                    upsertEntityType(subject, statement.object().value());
                }
            }
            runPropagationClosure();
            logger.fine(() -> "Applied statement assertion: " + statement);
            return "Assertion recorded.";
        }
        return "Unknown assertion payload.";
    }

    private boolean isQuestion(QueryGoal query) {
        return query.type() != QueryGoal.Type.UNKNOWN;
    }

    private boolean isYesNo(QueryGoal query) {
        return query.type() == QueryGoal.Type.YESNO;
    }

    private String resolveQuestionAfterAssertion(QueryGoal query, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            HeadContext followUpContext = new HeadContext(query, graph, ontology, workingMemory);
            List<ReasoningCandidate> followUp = reasoner.reason(followUpContext);
            if (followUp.isEmpty()) {
                return isYesNo(query) ? "Unknown." : "No candidates produced.";
            }
            ReasoningCandidate winner = followUp.get(0);
            trace.addEntry(new ReasoningTraceEntry(query, followUp, winner));
            if (CandidateType.ANSWER.equals(winner.type())) {
                logger.fine(() -> "Follow-up winner type=" + winner.type()
                        + " producedBy=" + winner.producedBy()
                        + " score=" + winner.score());
                return winner.payload().toString();
            }
            if (!CandidateType.ASSERTION.equals(winner.type())) {
                return winner.payload() == null ? "No payload." : winner.payload().toString();
            }
            applyCandidate(winner);
        }
        return "Assertion recorded.";
    }

    private String handleSingle(HeadContext context, QueryGoal query) {
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        if (candidates.isEmpty()) {
            if (isYesNo(query)) {
                return "Unknown.";
            }
            return "No candidates produced.";
        }
        ReasoningCandidate winner = candidates.get(0);
        trace.addEntry(new ReasoningTraceEntry(query, candidates, winner));
        logger.fine(() -> "Winner type=" + winner.type() + " producedBy=" + winner.producedBy()
                + " score=" + winner.score());
        String result = applyCandidate(winner);
        if (CandidateType.ASSERTION.equals(winner.type()) && isQuestion(query)) {
            return resolveQuestionAfterAssertion(query, 2);
        }
        return result;
    }

    private String handleWithSubgoals(QueryGoal root, Statement statement) {
        java.util.ArrayDeque<QueryGoal> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        int processed = 0;

        while (!queue.isEmpty() && processed < MAX_SUBGOALS) {
            QueryGoal current = queue.removeFirst();
            processed++;
            workingMemory.pushGoal(current);

            HeadContext context = new HeadContext(
                    current,
                    graph,
                    ontology,
                    current.goalId().equals(root.goalId()) ? statement : null,
                    workingMemory
            );
            List<ReasoningCandidate> candidates = reasoner.reason(context);
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
                int nextDepth = current.depth() + 1;
                if (nextDepth <= MAX_SUBGOAL_DEPTH) {
                    queue.addLast(subgoal.withParent(current.goalId(), nextDepth));
                }
                workingMemory.popGoal();
                continue;
            }

            if (CandidateType.ASSERTION.equals(winner.type())) {
                applyCandidate(winner);
                queue.addLast(root);
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
                workingMemory.popGoal();
                return winner.payload() == null ? "No payload." : winner.payload().toString();
            }
            workingMemory.popGoal();
        }

        return "No candidates produced.";
    }

    private ReasoningCandidate selectPreferredCandidate(List<ReasoningCandidate> candidates) {
        ReasoningCandidate winner = candidates.get(0);
        if (CandidateType.SUBGOAL.equals(winner.type())) {
            for (ReasoningCandidate candidate : candidates) {
                if (CandidateType.ASSERTION.equals(candidate.type())) {
                    return candidate;
                }
            }
        }
        return winner;
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
        RelationPropagationHead propagationHead = new RelationPropagationHead();
        int totalAdded = 0;

        for (int i = 0; i < MAX_PROPAGATION_ITERATIONS; i++) {
            List<ReasoningCandidate> candidates = propagationHead.evaluate(context);
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
