package com.sahr.examples;

import com.sahr.agent.SahrAgent;
import com.sahr.config.EngineConfig;
import com.sahr.config.HeadRegistry;
import com.sahr.config.OntologyContext;
import com.sahr.config.OntologyRegistry;
import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.EntityNode;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.core.ReasoningTraceEntry;
import com.sahr.core.RelationAssertion;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.core.WorkingMemory;
import com.sahr.nlp.InputFeatureExtractor;
import com.sahr.nlp.InputFeatures;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.ontology.SemanticNodeNormalizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReasoningHeadsEndToEndTest {
    private static final String CONFIG_RESOURCE = "sahr/engine-e2e.properties";

    private OntologyContext ontologyContext;
    private SahrReasoner reasoner;
    private SimpleQueryParser parser;
    private StatementParser statementParser;
    private SemanticNodeNormalizer semanticNormalizer;

    @BeforeAll
    void setUp() {
        EngineConfig config = EngineConfig.loadFromClasspath(CONFIG_RESOURCE);
        ontologyContext = OntologyRegistry.loadOntologyContext(config);
        reasoner = new SahrReasoner(HeadRegistry.buildHeads(config, ontologyContext));
        parser = new SimpleQueryParser(true);
        statementParser = new StatementParser(true);
        semanticNormalizer = new SemanticNodeNormalizer(ontologyContext.termMapper());
    }

    @Test
    void questionIntentHeadProducesIntentDecision() {
        ReasoningCandidate winner = selectIntentWinner("Why did the engine stop?");
        assertNotNull(winner);
        assertEquals("question-intent", winner.producedBy());
    }

    @Test
    void ruleIntentHeadProducesIntentDecision() {
        ReasoningCandidate winner = selectIntentWinner("If the motor fails, then the device stops.");
        assertNotNull(winner);
        assertEquals("rule-intent", winner.producedBy());
    }

    @Test
    void assertionIntentHeadProducesIntentDecision() {
        ReasoningCandidate winner = selectIntentWinner("The man is wearing a hat.");
        assertNotNull(winner);
        assertEquals("assertion-intent", winner.producedBy());
    }

    @Test
    void conditionQueryIntentHeadProducesIntentDecision() {
        List<ReasoningCandidate> candidates = selectIntentCandidates("If the system fails, what breaks?");
        assertTrue(candidates.stream().anyMatch(candidate -> "condition-query-intent".equals(candidate.producedBy())));
    }

    @Test
    void questionQueryHeadProposesSubgoal() {
        InputFeatures features = InputFeatureExtractor.extract("What is the man wearing?");
        HeadContext context = new HeadContext(QueryGoal.unknown(), new InMemoryKnowledgeBase(),
                ontologyContext.service(), null, null, new WorkingMemory(), features, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        assertTrue(candidates.stream().anyMatch(candidate -> "question-query".equals(candidate.producedBy())));
    }

    @Test
    void conditionQueryHeadProposesSubgoal() {
        SahrAgent agent = newAgent();
        int before = traceSize(agent);
        agent.handle("If the motor fails, then the device stops.");
        agent.handle("If the motor fails, what happens?");
        ReasoningTraceEntry entry = firstNewTraceEntry(agent, before + 1);
        assertEquals("condition-query", entry.winner().producedBy());
    }

    @Test
    void queryPlannerHeadProducesQueryPlan() {
        QueryGoal query = QueryGoal.relation("entity:battery", "power", "entity:engine", null);
        InputFeatures features = InputFeatureExtractor.extract("What powers the engine?");
        HeadContext context = new HeadContext(query, new InMemoryKnowledgeBase(),
                ontologyContext.service(), null, null, new WorkingMemory(), features, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        List<String> producerNames = candidates.stream()
                .map(ReasoningCandidate::producedBy)
                .distinct()
                .toList();
        if (!producerNames.contains("query-plan")) {
            throw new AssertionError("Query planner candidates: " + producerNames);
        }
    }

    @Test
    void assertionInsertionHeadRecordsAssertions() {
        SahrAgent agent = newAgent();
        String answer = agent.handle("The man is in the room.");
        assertEquals("Assertion recorded.", answer);
    }

    @Test
    void relationQueryHeadAnswersRelationQuestions() {
        SahrAgent agent = newAgent();
        agent.handle("The man is wearing a hat.");
        String answer = agent.handle("Who is wearing the hat?");
        assertTrue(containsAny(answer, "entity:man", "man"));
    }

    @Test
    void graphRetrievalHeadAnswersNestedLocations() {
        SahrAgent agent = newAgent();
        agent.handle("The apple is inside the basket.");
        agent.handle("The basket is in the kitchen.");
        String answer = agent.handle("Where is the apple?");
        assertTrue(containsAny(answer, "entity:apple inside entity:basket", "entity:apple in entity:basket"));
    }

    @Test
    void queryAlignmentHeadMatchesRangeFromOverlay() {
        SahrAgent agent = newAgent();
        agent.handle("The cat is inside the box.");
        String answer = agent.handle("Where is the cat?");
        assertTrue(containsAny(answer, "entity:cat inside entity:box", "entity:cat in entity:box"));
    }

    @Test
    void subgoalExpansionHeadTransfersLocations() {
        SahrAgent agent = newAgent();
        agent.handle("The woman is with the man.");
        agent.handle("The man is in the room.");
        String answer = agent.handle("Where is the woman?");
        assertTrue(answer.contains("entity:woman"));
        assertTrue(answer.contains("entity:room"));
    }

    @Test
    void ruleInsertionHeadRecordsRule() {
        SahrAgent agent = newAgent();
        String answer = agent.handle("If the motor fails, then the device stops.");
        assertEquals("Rule recorded.", answer);
    }

    @Test
    void ruleForwardChainHeadAppliesRule() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId motor = new SymbolId("entity:motor");
        SymbolId device = new SymbolId("entity:device");
        graph.addRule(new com.sahr.core.RuleAssertion(
                new RelationAssertion(motor, "https://sahr.ai/ontology/relations#fail", new SymbolId("concept:true"), 0.9),
                new RelationAssertion(device, "https://sahr.ai/ontology/relations#stop", new SymbolId("concept:true"), 0.9),
                0.9
        ));
        graph.addAssertion(new RelationAssertion(motor, "https://sahr.ai/ontology/relations#fail", new SymbolId("concept:true"), 0.9));
        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        assertTrue(candidates.stream().anyMatch(candidate -> "rule-forward-chain".equals(candidate.producedBy())));
    }

    @Test
    void yesNoRelationQueriesAnswerAffirmatively() {
        SahrAgent agent = newAgent();
        agent.handle("The man is wearing a hat.");
        String answer = agent.handle("Is the man wearing a hat?");
        assertTrue(answer.toLowerCase(Locale.ROOT).startsWith("yes"));
    }

    @Test
    void countRelationQueriesReturnTotals() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        String hatType = semanticNormalizer.canonicalType("hat")
                .orElseGet(() -> ontologyContext.termMapper().mapToken("hat").orElse("concept:hat"));
        graph.addEntity(new EntityNode(new SymbolId("entity:hat1"), "hat1", Set.of("hat", hatType)));
        graph.addEntity(new EntityNode(new SymbolId("entity:hat2"), "hat2", Set.of("hat", hatType)));
        SahrAgent agent = newAgent(graph);
        agent.handle("Hat1 is with the man.");
        agent.handle("Hat2 is with the man.");
        String answer = agent.handle("How many hat are with the man?");
        assertTrue(containsAny(answer, "2", "two"), "Count answer was: " + answer);
    }

    @Test
    void attributeQueriesReturnAttributeValues() {
        Assumptions.assumeTrue(attributeHeadEnabled(), "Attribute query head not enabled in runtime.");
        SahrAgent agent = newAgent();
        agent.handle("The box is red.");
        String answer = agent.handle("What color is the box?");
        assertTrue(answer.toLowerCase(Locale.ROOT).contains("red"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transitiveCases")
    void transitiveHeadsDeriveAssertions(String headName, String predicate) {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId a = new SymbolId("entity:a");
        SymbolId b = new SymbolId("entity:b");
        SymbolId c = new SymbolId("entity:c");
        graph.addAssertion(new RelationAssertion(a, predicate, b, 0.9));
        graph.addAssertion(new RelationAssertion(b, predicate, c, 0.9));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.type() == CandidateType.ASSERTION
                        && headName.equals(candidate.producedBy())
                        && candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).subject().equals(a)
                        && ((RelationAssertion) candidate.payload()).object().equals(c)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalizationCases")
    void normalizationHeadsDeriveInAssertions(String headName, String inputPredicate) {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId a = new SymbolId("entity:a");
        SymbolId b = new SymbolId("entity:b");
        graph.addAssertion(new RelationAssertion(a, inputPredicate, b, 0.9));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.type() == CandidateType.ASSERTION
                        && headName.equals(candidate.producedBy())
                        && candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().endsWith("in")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transferCases")
    void locationTransferHeadsDeriveLocation(String headName, String predicate, boolean reverse) {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId subject = new SymbolId("entity:subject");
        SymbolId object = new SymbolId("entity:object");
        SymbolId location = new SymbolId("entity:room");
        graph.addEntity(new EntityNode(subject, "subject", Set.of("https://en-word.net/id/oewn-00007846-n")));
        graph.addEntity(new EntityNode(object, "object", Set.of("https://en-word.net/id/oewn-00007846-n")));
        if (reverse) {
            graph.addAssertion(new RelationAssertion(subject, predicate, object, 0.9));
            graph.addAssertion(new RelationAssertion(object, "https://sahr.ai/ontology/relations#in", location, 0.9));
        } else {
            graph.addAssertion(new RelationAssertion(subject, predicate, object, 0.9));
            graph.addAssertion(new RelationAssertion(subject, "https://sahr.ai/ontology/relations#in", location, 0.9));
        }

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.type() == CandidateType.ASSERTION
                        && headName.equals(candidate.producedBy())
                        && candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).predicate().endsWith("in")
                        && (reverse
                        ? ((RelationAssertion) candidate.payload()).subject().equals(subject)
                        : ((RelationAssertion) candidate.payload()).subject().equals(object))
                        && ((RelationAssertion) candidate.payload()).object().equals(location)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dependencyCases")
    void dependencyHeadsDeriveChains(String headName, String firstPredicate, String secondPredicate) {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SymbolId a = new SymbolId("entity:a");
        SymbolId b = new SymbolId("entity:b");
        SymbolId c = new SymbolId("entity:c");
        graph.addAssertion(new RelationAssertion(a, firstPredicate, b, 0.9));
        graph.addAssertion(new RelationAssertion(b, secondPredicate, c, 0.9));

        HeadContext context = new HeadContext(QueryGoal.unknown(), graph, ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.type() == CandidateType.ASSERTION
                        && headName.equals(candidate.producedBy())
                        && candidate.payload() instanceof RelationAssertion
                        && ((RelationAssertion) candidate.payload()).subject().equals(a)
                        && ((RelationAssertion) candidate.payload()).object().equals(c)));
    }

    private ReasoningCandidate selectIntentWinner(String input) {
        return selectIntentCandidates(input).stream()
                .max((left, right) -> Double.compare(left.score(), right.score()))
                .orElse(null);
    }

    private List<ReasoningCandidate> selectIntentCandidates(String input) {
        InputFeatures features = InputFeatureExtractor.extract(input);
        HeadContext context = new HeadContext(QueryGoal.unknown(), new InMemoryKnowledgeBase(),
                ontologyContext.service(), null, null, new WorkingMemory(), features, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        List<ReasoningCandidate> intents = new ArrayList<>();
        for (ReasoningCandidate candidate : candidates) {
            if (candidate.type() == CandidateType.INTENT) {
                intents.add(candidate);
            }
        }
        return intents;
    }

    private SahrAgent newAgent() {
        return newAgent(new InMemoryKnowledgeBase());
    }

    private SahrAgent newAgent(InMemoryKnowledgeBase graph) {
        return new SahrAgent(
                graph,
                ontologyContext.service(),
                reasoner,
                parser,
                statementParser,
                ontologyContext.termMapper()
        );
    }

    private int traceSize(SahrAgent agent) {
        return agent.trace().map(trace -> trace.entries().size()).orElse(0);
    }

    private ReasoningTraceEntry firstNewTraceEntry(SahrAgent agent, int startIndex) {
        List<ReasoningTraceEntry> entries = agent.trace().map(trace -> trace.entries()).orElse(List.of());
        assertTrue(entries.size() > startIndex);
        return entries.get(startIndex);
    }

    private boolean attributeHeadEnabled() {
        HeadContext context = new HeadContext(QueryGoal.attribute("entity:box", "color"),
                new InMemoryKnowledgeBase(), ontologyContext.service(), null, null,
                new WorkingMemory(), null, semanticNormalizer);
        List<ReasoningCandidate> candidates = reasoner.reason(context);
        return candidates.stream().anyMatch(candidate -> "attribute-query".equals(candidate.producedBy()));
    }

    private static boolean containsAny(String value, String... options) {
        if (value == null) {
            return false;
        }
        for (String option : options) {
            if (option == null) {
                continue;
            }
            if (value.toLowerCase(Locale.ROOT).contains(option.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Stream<Arguments> transitiveCases() {
        return Stream.of(
                Arguments.of("transitive-in", "https://sahr.ai/ontology/relations#in"),
                Arguments.of("transitive-inside", "https://sahr.ai/ontology/relations#inside"),
                Arguments.of("transitive-locatedIn", "https://sahr.ai/ontology/relations#locatedIn")
        );
    }

    private static Stream<Arguments> normalizationCases() {
        return Stream.of(
                Arguments.of("inside-to-in", "https://sahr.ai/ontology/relations#inside"),
                Arguments.of("locatedIn-to-in", "https://sahr.ai/ontology/relations#locatedIn")
        );
    }

    private static Stream<Arguments> transferCases() {
        List<Arguments> cases = new ArrayList<>();
        addTransferPair(cases, "with", "with-location-transfer", "with-location-reverse");
        addTransferPair(cases, "wear", "wear-location-transfer", "wear-location-reverse");
        addTransferPair(cases, "wornBy", "wornBy-location-transfer", "wornBy-location-reverse");
        addTransferPair(cases, "carry", "carry-location-transfer", "carry-location-reverse");
        addTransferPair(cases, "hold", "hold-location-transfer", "hold-location-reverse");
        addTransferPair(cases, "possess", "possess-location-transfer", "possess-location-reverse");
        addTransferPair(cases, "have", "have-location-transfer", "have-location-reverse");
        addTransferPair(cases, "opposite", "opposite-location-transfer", "opposite-location-reverse");
        addTransferPair(cases, "partOf", "partOf-location-transfer", "partOf-location-reverse");
        addTransferPair(cases, "near", "near-location-transfer", "near-location-reverse");
        addTransferPair(cases, "beside", "beside-location-transfer", "beside-location-reverse");
        addTransferPair(cases, "alongside", "alongside-location-transfer", "alongside-location-reverse");
        addTransferPair(cases, "nextTo", "nextTo-location-transfer", "nextTo-location-reverse");
        addTransferPair(cases, "colocation", "colocation-location-transfer", "colocation-location-reverse");
        return cases.stream();
    }

    private static void addTransferPair(List<Arguments> cases,
                                        String predicate,
                                        String transferHead,
                                        String reverseHead) {
        String iri = "https://sahr.ai/ontology/relations#" + predicate;
        cases.add(Arguments.of(transferHead, iri, false));
        cases.add(Arguments.of(reverseHead, iri, true));
    }

    private static Stream<Arguments> dependencyCases() {
        return Stream.of(
                Arguments.of("poweredBy-transitive",
                        "https://sahr.ai/ontology/relations#poweredBy",
                        "https://sahr.ai/ontology/relations#poweredBy"),
                Arguments.of("poweredBy-chargedBy",
                        "https://sahr.ai/ontology/relations#poweredBy",
                        "https://sahr.ai/ontology/relations#chargedBy"),
                Arguments.of("chargedBy-transitive",
                        "https://sahr.ai/ontology/relations#chargedBy",
                        "https://sahr.ai/ontology/relations#chargedBy")
        );
    }
}
