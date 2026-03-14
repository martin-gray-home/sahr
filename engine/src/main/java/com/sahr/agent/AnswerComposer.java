package com.sahr.agent;

import com.sahr.core.EntityNode;
import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.QueryGoal;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AnswerComposer {
    private enum AnswerRole {
        ROOT_FAILURE_COMPONENT,
        EVIDENCE_SIGNAL,
        RECOVERY_AGENT
    }

    interface Support {
        String localName(String predicate);
        Boolean booleanConcept(SymbolId id);
        String normalizeTypeToken(String raw);
        String lastInput();
        SymbolId selectCauseNode(RelationAssertion antecedent);
    }

    private static final String PREDICATE_TYPE = "rdf:type";
    private static final String TIMING_PROPERTY = "sahr.subgoal.timing";

    private final KnowledgeBase graph;
    private final OntologyService ontology;
    private final AnswerRenderer answerRenderer;
    private final AnswerRanker answerRanker;
    private final ExplanationChainBuilder explanationChains;
    private final ForwardChainSearch forwardChainSearch;
    private final PredicateExplainer predicateExplainer;
    private final AliasBridge aliasBridge;
    private final OntologyAnnotationResolver annotationResolver;
    private final Support support;
    private final Object indexLock = new Object();
    private long indexVersion = -1;
    private Map<String, Set<SymbolId>> labelTokenIndex = new HashMap<>();
    private Map<SymbolId, List<String>> labelPhrasesByEntity = new HashMap<>();
    private Map<SymbolId, Set<SymbolId>> dependentsByResource = new HashMap<>();
    private Map<String, List<RelationAssertion>> assertionsByLocalPredicate = new HashMap<>();
    private Map<String, Double> evidencePredicateWeight = new HashMap<>();
    private Map<SymbolId, Double> evidenceSignalWeightBySubject = new HashMap<>();
    private Map<SymbolId, Boolean> temporalContextBySymbol = new HashMap<>();
    private Map<SymbolId, String> entityIriCache = new HashMap<>();
    private Map<String, Boolean> semanticRoleCache = new HashMap<>();
    private Map<String, Double> temporalSupportCache = new HashMap<>();
    private Map<String, Double> evidenceAlignmentCache = new HashMap<>();
    private Map<String, List<String>> structuredChainCache = new HashMap<>();
    private Map<String, String> evidenceSignalCache = new HashMap<>();
    private Map<String, String> dependencyFailureCache = new HashMap<>();
    private Map<String, ExplanationCandidate> explanationCandidateCache = new HashMap<>();
    private Map<String, Boolean> temporalContextCache = new HashMap<>();

    AnswerComposer(KnowledgeBase graph,
                   OntologyService ontology,
                   AnswerRenderer answerRenderer,
                   AnswerRanker answerRanker,
                   ExplanationChainBuilder explanationChains,
                   ForwardChainSearch forwardChainSearch,
                   PredicateExplainer predicateExplainer,
                   AliasBridge aliasBridge,
                   OntologyAnnotationResolver annotationResolver,
                   Support support) {
        this.graph = graph;
        this.ontology = ontology;
        this.answerRenderer = answerRenderer;
        this.answerRanker = answerRanker;
        this.explanationChains = explanationChains;
        this.forwardChainSearch = forwardChainSearch;
        this.predicateExplainer = predicateExplainer;
        this.aliasBridge = aliasBridge;
        this.annotationResolver = annotationResolver;
        this.support = support;
    }

    void noteEntity(EntityNode entity) {
        if (entity == null || entity.id() == null) {
            return;
        }
        synchronized (indexLock) {
            removeEntityTokens(entity.id());
            List<String> phrases = buildLabelPhrases(entity.id());
            labelPhrasesByEntity.put(entity.id(), phrases);
            for (String phrase : phrases) {
                for (String token : tokenize(phrase)) {
                    labelTokenIndex.computeIfAbsent(token, key -> new HashSet<>()).add(entity.id());
                }
            }
            temporalSupportCache.clear();
            evidenceAlignmentCache.clear();
            structuredChainCache.clear();
            evidenceSignalCache.clear();
            dependencyFailureCache.clear();
            explanationCandidateCache.clear();
            temporalContextCache.clear();
            semanticRoleCache.clear();
            entityIriCache.clear();
            indexVersion = graph.version();
        }
    }

    void noteAssertion(RelationAssertion assertion) {
        if (assertion == null) {
            return;
        }
        synchronized (indexLock) {
            String local = support.localName(assertion.predicate());
            assertionsByLocalPredicate
                    .computeIfAbsent(local, key -> new ArrayList<>())
                    .add(assertion);
            if ("poweredby".equals(local) || "require".equals(local) || "requires".equals(local)) {
                dependentsByResource
                        .computeIfAbsent(assertion.object(), key -> new HashSet<>())
                        .add(assertion.subject());
            }
            if ("before".equals(local) || "after".equals(local) || "during".equals(local)) {
                if (assertion.subject() != null) {
                    temporalContextBySymbol.put(assertion.subject(), true);
                }
                if (assertion.object() != null) {
                    temporalContextBySymbol.put(assertion.object(), true);
                }
            }
            double weight = predicateEvidenceWeightCached(assertion.predicate());
            if (weight > 0.0 && hasSemanticRole(assertion.subject(), "evidence_signal")) {
                Double existing = evidenceSignalWeightBySubject.get(assertion.subject());
                if (existing == null || weight > existing) {
                    evidenceSignalWeightBySubject.put(assertion.subject(), weight);
                }
            }
            temporalSupportCache.clear();
            evidenceAlignmentCache.clear();
            structuredChainCache.clear();
            evidenceSignalCache.clear();
            dependencyFailureCache.clear();
            explanationCandidateCache.clear();
            temporalContextCache.clear();
            indexVersion = graph.version();
        }
    }

    String relationshipAnswer(QueryGoal goal) {
        if (!wantsRelationshipChain(goal)) {
            return null;
        }
        String relationship = relationshipChainAnswer(goal);
        if (relationship == null || relationship.isBlank()) {
            relationship = relationshipChainFallback(goal);
        }
        return relationship;
    }

    boolean isRelationshipQuestion(QueryGoal goal) {
        return wantsRelationshipChain(goal);
    }

    // intentionally left without list-routing helpers; list isolation is handled in projection logic.

    String resolveTemporalComponentFailure(QueryGoal goal) {
        if (goal == null) {
            return null;
        }
        String predicate = support.localName(goal.predicate());
        if (!"fail".equals(predicate)) {
            return null;
        }
        if (!expectsTypeLabel(goal, "component")) {
            return null;
        }
        SymbolId target = resolveTemporalFailureTarget(goal);
        if (target == null) {
            return null;
        }
        ExplanationCandidate candidate = explanationCandidate(target, 4);
        SymbolId failure = selectBestFailure(candidate, true);
        return failure == null ? null : failure.value();
    }

    String executeCauseChain(QueryGoal goal) {
        boolean timing = Boolean.parseBoolean(System.getProperty(TIMING_PROPERTY, "false"));
        String predicate = support.localName(goal.predicate());
        SymbolId subject = (goal.subject() != null && !goal.subject().isBlank())
                ? new SymbolId(goal.subject())
                : null;
        SymbolId target = (goal.object() != null && !goal.object().isBlank())
                ? new SymbolId(goal.object())
                : null;
        if (target == null && goal.subject() != null) {
            target = new SymbolId(goal.subject());
        }
        if ("backupfor".equals(predicate)) {
            long start = timing ? System.nanoTime() : 0L;
            String backupExplanation = backupForFallback(subject, target);
            if (timing) {
                logCauseTiming("backup_for_fallback", goal, System.nanoTime() - start, null);
            }
            if (backupExplanation != null) {
                return backupExplanation;
            }
        }
        boolean chainRequest = wantsChainExplanation(goal) || wantsEvidenceAlignedChain(goal);
        if (!chainRequest) {
            long evidenceStart = timing ? System.nanoTime() : 0L;
            String evidenceSignal = evidenceSignalAnswer(goal);
            if (timing) {
                logCauseTiming("evidence_signal", goal, System.nanoTime() - evidenceStart, null);
            }
            if (evidenceSignal != null) {
                return evidenceSignal;
            }
        }
        long dependencyListStart = timing ? System.nanoTime() : 0L;
        String dependencyList = dependencyFailureListAnswer(goal);
        if (timing) {
            logCauseTiming("dependency_failure_list", goal, System.nanoTime() - dependencyListStart, null);
        }
        if (dependencyList != null) {
            return dependencyList;
        }
        long relationshipStart = timing ? System.nanoTime() : 0L;
        String relationship = relationshipAnswer(goal);
        if (timing) {
            logCauseTiming("relationship_answer", goal, System.nanoTime() - relationshipStart, null);
        }
        if (relationship != null && !relationship.isBlank()) {
            return relationship;
        }
        if (wantsRuledOutCauses(goal)) {
            long ruledOutStart = timing ? System.nanoTime() : 0L;
            String ruledOut = ruledOutCauseAnswer(goal, target);
            if (timing) {
                logCauseTiming("ruled_out", goal, System.nanoTime() - ruledOutStart, null);
            }
            if (ruledOut != null) {
                return ruledOut;
            }
        }
        if (wantsDependencyContrast(goal)) {
            long contrastStart = timing ? System.nanoTime() : 0L;
            String contrast = dependencyContrastAnswer(goal);
            if (timing) {
                logCauseTiming("dependency_contrast", goal, System.nanoTime() - contrastStart, null);
            }
            if (contrast != null) {
                return contrast;
            }
        }
        if (wantsConditionContrast(goal)) {
            long contrastStart = timing ? System.nanoTime() : 0L;
            String contrast = conditionContrastAnswer(goal);
            if (timing) {
                logCauseTiming("condition_contrast", goal, System.nanoTime() - contrastStart, null);
            }
            if (contrast != null) {
                return contrast;
            }
        }
        if (!predicate.isBlank() && !"cause".equals(predicate) && !"causedby".equals(predicate)) {
            long predicateStart = timing ? System.nanoTime() : 0L;
            List<String> predicateExplanation = predicateExplainer.buildPredicateExplanation(goal, predicate, 3);
            if (timing) {
                logCauseTiming("predicate_explainer", goal, System.nanoTime() - predicateStart, null);
            }
            if (!predicateExplanation.isEmpty()) {
                long explanationStart = timing ? System.nanoTime() : 0L;
                ExplanationCandidate candidate = explanationCandidate(target, 4);
                if (timing) {
                    logCauseTiming("build_candidate_predicate", goal, System.nanoTime() - explanationStart, null);
                }
                if (wantsRecoveryAgent(goal, predicate)) {
                    long agentStart = timing ? System.nanoTime() : 0L;
                    SymbolId agent = selectBestRecoveryAgent(candidate, target);
                    if (timing) {
                        logCauseTiming("select_recovery_agent", goal, System.nanoTime() - agentStart, null);
                    }
                    if (agent != null) {
                        return renderEntityAnswer(agent.value(), goal, AnswerRole.RECOVERY_AGENT);
                    }
                }
                long summaryStart = timing ? System.nanoTime() : 0L;
                String explanation = summarizeRecoveryExplanation(goal, predicate, candidate, predicateExplanation);
                if (timing) {
                    logCauseTiming("summarize_recovery", goal, System.nanoTime() - summaryStart, null);
                }
                if (explanation != null) {
                    return explanation;
                }
                return String.join("\n", predicateExplanation);
            }
        }
        if (target == null) {
            return "No candidates produced.";
        }
        long structuredStart = timing ? System.nanoTime() : 0L;
        ExplanationCandidate structuredCandidate = explanationCandidate(target, 4);
        if (timing) {
            logCauseTiming("build_candidate_structured", goal, System.nanoTime() - structuredStart, null);
        }
        if (structuredCandidate != null
                && (wantsChainExplanation(goal)
                || wantsRecoveryClause(goal)
                || "cause".equals(predicate)
                || "causedby".equals(predicate)
                || "restore".equals(predicate)
                || "regain".equals(predicate))) {
            long chainStart = timing ? System.nanoTime() : 0L;
            List<String> structured = buildStructuredChain(structuredCandidate, goal, target, predicate);
            if (timing) {
                logCauseTiming("build_structured_chain", goal, System.nanoTime() - chainStart, null);
            }
            List<String> candidateSentences = structuredCandidate.sentences();
            if (preferRicherChain(goal, structured, candidateSentences)) {
                return joinWithOutcome(candidateSentences, goal, target);
            }
            if (!structured.isEmpty()) {
                return joinWithOutcome(structured, goal, target);
            }
            if (candidateSentences != null && !candidateSentences.isEmpty()) {
                return joinWithOutcome(candidateSentences, goal, target);
            }
            long bestChainStart = timing ? System.nanoTime() : 0L;
            String chain = bestFailureChainToOutcome(structuredCandidate, target, goal);
            if (timing) {
                logCauseTiming("best_failure_chain", goal, System.nanoTime() - bestChainStart, null);
            }
            if (chain != null) {
                return joinWithOutcome(splitLines(chain), goal, target);
            }
        }
        List<SymbolId> subjectCandidates = aliasBridge.expandAliasSymbols(subject);
        List<SymbolId> targetCandidates = aliasBridge.expandAliasSymbols(target);
        if (subject != null && target != null && !subject.equals(target)) {
            ForwardChainSearch.ChainResult best = null;
            long forwardTotal = 0L;
            int forwardCount = 0;
            for (SymbolId subjectCandidate : subjectCandidates) {
                for (SymbolId targetCandidate : targetCandidates) {
                    if (subjectCandidate.equals(targetCandidate)) {
                        continue;
                    }
                    long forwardStart = timing ? System.nanoTime() : 0L;
                    ForwardChainSearch.ChainResult forward = forwardChainSearch.search(
                            subjectCandidate,
                            targetCandidate,
                            4
                    );
                    if (timing) {
                        forwardTotal += (System.nanoTime() - forwardStart);
                        forwardCount++;
                    }
                    if (forward == null || forward.sentences().isEmpty()) {
                        continue;
                    }
                    if (best == null || forward.score() > best.score()) {
                        best = forward;
                    }
                }
            }
            if (timing && forwardCount > 0) {
                logCauseTiming("forward_chain_search", goal, forwardTotal, "count=" + forwardCount);
            }
            if (best != null) {
                return String.join("\n", best.sentences());
            }
        }
        if (subject == null && target != null) {
            for (SymbolId targetCandidate : targetCandidates) {
                long ruleStart = timing ? System.nanoTime() : 0L;
                String ruleChain = ruleChainFallback(targetCandidate);
                if (timing) {
                    logCauseTiming("rule_chain_fallback", goal, System.nanoTime() - ruleStart, null);
                }
                if (ruleChain != null) {
                    return ruleChain;
                }
            }
        }
        return "No candidates produced.";
    }

    private void logCauseTiming(String step, QueryGoal goal, long durationNs, String extra) {
        if (!Boolean.parseBoolean(System.getProperty(TIMING_PROPERTY, "false"))) {
            return;
        }
        String subject = goal == null ? "" : safe(goal.subject());
        String predicate = goal == null ? "" : safe(goal.predicate());
        String object = goal == null ? "" : safe(goal.object());
        String suffix = (extra == null || extra.isBlank()) ? "" : " " + extra;
        System.out.println("cause_timing step=" + step
                + " ms=" + Math.round(durationNs / 1_000_000.0)
                + " subject=\"" + subject + "\""
                + " predicate=\"" + predicate + "\""
                + " object=\"" + object + "\""
                + suffix);
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String relationshipChainAnswer(QueryGoal goal) {
        if (goal == null || !wantsRelationshipChain(goal)) {
            return null;
        }
        List<SymbolId> mentions = extractMentionedEntities(goal);
        if (mentions.size() < 2) {
            return null;
        }
        List<String> path = relationshipPath(mentions, 6);
        if (needsControlBridge(goal, mentions, path)) {
            String bridge = controlBridgeSentence(goal, mentions);
            if (bridge != null && !bridge.isBlank()) {
                path.add(bridge);
            }
        }
        if (!path.isEmpty()) {
            return String.join("\n", path);
        }
        return null;
    }

    private String relationshipChainFallback(QueryGoal goal) {
        if (goal == null) {
            return null;
        }
        List<SymbolId> mentions = extractMentionedEntities(goal);
        if (mentions.size() < 2) {
            return null;
        }
        List<RelationAssertion> matched = new ArrayList<>();
        List<Set<SymbolId>> mentionAliases = new ArrayList<>();
        for (SymbolId mention : mentions) {
            mentionAliases.add(relationshipAliasSet(mention));
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (assertion.subject() == null || assertion.object() == null) {
                continue;
            }
            String predicateName = support.localName(assertion.predicate());
            if (!isRelationshipPredicate(predicateName)) {
                continue;
            }
            if (connectsMentionPair(assertion, mentionAliases)) {
                matched.add(assertion);
            }
        }
        if (matched.isEmpty()) {
            return null;
        }
        matched.sort(Comparator.comparingInt(a -> relationshipPredicatePriority(support.localName(a.predicate()))));
        LinkedHashSet<String> sentences = new LinkedHashSet<>();
        for (RelationAssertion assertion : matched) {
            sentences.add(answerRenderer.formatAssertionSentence(assertion));
            if (sentences.size() >= 5) {
                break;
            }
        }
        return String.join("\n", sentences);
    }

    private List<String> relationshipPath(List<SymbolId> mentions, int maxDepth) {
        if (mentions == null || mentions.size() < 2) {
            return List.of();
        }
        LinkedHashSet<String> sentences = new LinkedHashSet<>();
        for (int i = 0; i < mentions.size(); i++) {
            for (int j = i + 1; j < mentions.size(); j++) {
                List<String> chain = undirectedChain(mentions.get(i), mentions.get(j), maxDepth);
                if (!chain.isEmpty()) {
                    sentences.addAll(chain);
                }
            }
        }
        return new ArrayList<>(sentences);
    }

    private boolean needsControlBridge(QueryGoal goal, List<SymbolId> mentions, List<String> path) {
        if (goal == null || mentions == null || mentions.isEmpty()) {
            return false;
        }
        boolean hasControlTarget = findControlMention(mentions) != null;
        if (!hasControlTarget) {
            return false;
        }
        if (path == null || path.isEmpty()) {
            return true;
        }
        for (String line : path) {
            if (line != null && line.toLowerCase(Locale.ROOT).contains("control")) {
                return false;
            }
        }
        return true;
    }

    private SymbolId findControlMention(List<SymbolId> mentions) {
        for (SymbolId mention : mentions) {
            if (mention == null) {
                continue;
            }
            if (hasSemanticRole(mention, "control_target")) {
                return mention;
            }
        }
        return null;
    }

    private String controlBridgeSentence(QueryGoal goal, List<SymbolId> mentions) {
        SymbolId controlTarget = findControlMention(mentions);
        if (controlTarget == null) {
            return null;
        }
        SymbolId actuator = findActuatorMention(mentions);
        if (actuator == null) {
            return null;
        }
        String subjectText = displayValue(actuator);
        String objectText = displayValue(controlTarget);
        String lowerObject = objectText.toLowerCase(Locale.ROOT);
        if (lowerObject.startsWith("control ")) {
            objectText = objectText.substring("control ".length()).trim();
        }
        String verb = isPlural(subjectText) ? "control" : "controls";
        return subjectText + " " + verb + " " + objectText + ".";
    }

    private SymbolId findActuatorMention(List<SymbolId> mentions) {
        for (SymbolId mention : mentions) {
            if (mention == null) {
                continue;
            }
            String value = mention.value().toLowerCase(Locale.ROOT);
            if (value.contains("actuator")) {
                return mention;
            }
            var entityOpt = graph.findEntity(mention);
            if (entityOpt.isPresent()) {
                for (String type : entityOpt.get().conceptTypes()) {
                    if (type.toLowerCase(Locale.ROOT).contains("actuator")) {
                        return mention;
                    }
                }
            }
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
            if (!"contain".equals(predicate) && !"contains".equals(predicate)) {
                continue;
            }
            if (mentions.contains(assertion.subject()) && mentions.contains(assertion.object())) {
                return assertion.subject();
            }
        }
        return null;
    }

    private List<String> undirectedChain(SymbolId start, SymbolId target, int maxDepth) {
        if (start == null || target == null) {
            return List.of();
        }
        ArrayDeque<SymbolId> queue = new ArrayDeque<>();
        Map<SymbolId, RelationAssertion> edge = new HashMap<>();
        Map<SymbolId, SymbolId> parent = new HashMap<>();
        queue.add(start);
        parent.put(start, null);
        int depth = 0;
        while (!queue.isEmpty() && depth <= maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                SymbolId current = queue.removeFirst();
                if (current.equals(target)) {
                    return renderPath(current, parent, edge);
                }
                Set<SymbolId> aliasSet = relationshipAliasSet(current);
                for (RelationAssertion assertion : graph.getAllAssertions()) {
                    if (assertion.subject() == null || assertion.object() == null) {
                        continue;
                    }
                    String predicateName = support.localName(assertion.predicate());
                    if (!isRelationshipPredicate(predicateName)) {
                        continue;
                    }
                    SymbolId next = null;
                    if (aliasSet.contains(assertion.subject())) {
                        next = assertion.object();
                    } else if (aliasSet.contains(assertion.object())) {
                        next = assertion.subject();
                    }
                    if (next == null || parent.containsKey(next)) {
                        continue;
                    }
                    if (support.booleanConcept(next) != null) {
                        continue;
                    }
                    parent.put(next, current);
                    edge.put(next, assertion);
                    queue.addLast(next);
                }
                graph.findEntity(current).ifPresent(entity -> {
                    for (String type : entity.conceptTypes()) {
                        SymbolId typeId = new SymbolId(type);
                        if (!parent.containsKey(typeId)) {
                            RelationAssertion typeAssertion = new RelationAssertion(current, PREDICATE_TYPE, typeId, 0.6);
                            parent.put(typeId, current);
                            edge.put(typeId, typeAssertion);
                            queue.addLast(typeId);
                        }
                    }
                });
            }
            depth++;
        }
        return List.of();
    }

    private List<String> renderPath(SymbolId end,
                                    Map<SymbolId, SymbolId> parent,
                                    Map<SymbolId, RelationAssertion> edge) {
        ArrayDeque<String> stack = new ArrayDeque<>();
        SymbolId current = end;
        while (current != null && parent.containsKey(current)) {
            RelationAssertion assertion = edge.get(current);
            if (assertion != null) {
                stack.addFirst(answerRenderer.formatAssertionSentence(assertion));
            }
            current = parent.get(current);
        }
        return new ArrayList<>(stack);
    }

    private boolean wantsRelationshipChain(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        for (String text : goalTextFragments(goal)) {
            if (text == null) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("relationship") || normalized.contains("between")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "relationship", "between");
    }

    private boolean wantsRuledOutCauses(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        for (String text : goalTextFragments(goal)) {
            if (text == null) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("ruled out") || normalized.contains("rule out")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "ruled out", "rule out");
    }

    private boolean wantsDependencyContrast(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        if (containsCue(support.lastInput(), "under what conditions")) {
            return false;
        }
        for (String text : goalTextFragments(goal)) {
            if (text == null) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("did not depend") || normalized.contains("not depend")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "did not depend", "not depend");
    }

    private boolean wantsConditionContrast(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        for (String text : goalTextFragments(goal)) {
            if (text == null) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("under what conditions") || normalized.contains("would") && normalized.contains("still function")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "under what conditions");
    }

    private List<String> goalTextFragments(QueryGoal goal) {
        List<String> fragments = new ArrayList<>();
        fragments.add(goal.modifier());
        fragments.add(goal.subjectText());
        fragments.add(goal.objectText());
        fragments.add(goal.predicateText());
        return fragments;
    }

    private boolean containsCue(String text, String... cues) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String cue : cues) {
            if (cue == null || cue.isBlank()) {
                continue;
            }
            if (normalized.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    private SymbolId resolveTemporalFailureTarget(QueryGoal goal) {
        if (goal.object() != null && !goal.object().isBlank()) {
            return new SymbolId(goal.object());
        }
        if (goal.subject() != null && !goal.subject().isBlank()) {
            return new SymbolId(goal.subject());
        }
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            SymbolId matched = matchEntityByOntologyLabel(fragment);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private boolean expectsTypeLabel(QueryGoal goal, String labelToken) {
        if (goal == null || labelToken == null || labelToken.isBlank()) {
            return false;
        }
        String expected = normalizeExpectedType(goal.expectedType());
        if (matchesOntologyLabel(expected, labelToken)) {
            return true;
        }
        String entityType = normalizeExpectedType(goal.entityType());
        return matchesOntologyLabel(entityType, labelToken);
    }

    private String normalizeExpectedType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("entity:")) {
            trimmed = trimmed.substring("entity:".length());
        } else if (trimmed.startsWith("concept:")) {
            trimmed = trimmed.substring("concept:".length());
        }
        return annotationResolver.normalizeLabelToToken(trimmed);
    }

    private boolean matchesOntologyLabel(String normalizedValue, String labelToken) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return false;
        }
        String normalizedLabel = annotationResolver.normalizeLabelToToken(labelToken);
        if (normalizedValue.equals(normalizedLabel)) {
            return true;
        }
        String iri = normalizedValue.startsWith("http://") || normalizedValue.startsWith("https://")
                ? normalizedValue
                : null;
        if (iri != null) {
            for (String label : annotationResolver.labelsForIri(iri)) {
                if (annotationResolver.normalizeLabelToToken(label).equals(normalizedLabel)) {
                    return true;
                }
            }
            return false;
        }
        for (String iriCandidate : annotationResolver.entityIrisByLabel(normalizedValue)) {
            for (String label : annotationResolver.labelsForIri(iriCandidate)) {
                if (annotationResolver.normalizeLabelToToken(label).equals(normalizedLabel)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOpenDependencyProbe(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        String predicate = support.localName(goal.predicate());
        if (!"poweredby".equals(predicate) && !"require".equals(predicate) && !"requires".equals(predicate)) {
            return false;
        }
        return goal.object() == null || goal.object().isBlank();
    }

    private List<SymbolId> extractMentionedEntities(QueryGoal goal) {
        LinkedHashSet<SymbolId> mentions = new LinkedHashSet<>();
        if (goal.subject() != null && !goal.subject().isBlank()) {
            mentions.add(new SymbolId(goal.subject()));
        }
        if (goal.object() != null && !goal.object().isBlank()) {
            mentions.add(new SymbolId(goal.object()));
        }
        if (containsCue(support.lastInput(), "relationship between") || containsCue(support.lastInput(), "under what conditions")) {
            for (String phrase : relationshipPhrasesFromInput(support.lastInput())) {
                if (phrase.isBlank()) {
                    continue;
                }
                SymbolId matched = matchEntityByPhraseExact(phrase);
                if (matched == null) {
                    matched = matchEntityBySurfaceTokens(phrase);
                }
                if (matched == null) {
                    matched = fallbackEntityFromPhrase(phrase);
                }
                if (matched == null) {
                    matched = matchEntityByOntologyLabel(phrase);
                }
                if (matched != null) {
                    mentions.add(matched);
                }
            }
            if (mentions.size() < 2) {
                mentions.addAll(surfaceMentionFallback(support.lastInput(), 4));
            }
            return new ArrayList<>(mentions);
        }
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            for (String phrase : splitMentionPhrases(fragment)) {
                if (phrase.isBlank()) {
                    continue;
                }
                SymbolId matched = matchEntityByPhraseExact(phrase);
                if (matched == null) {
                    matched = matchEntityBySurfaceTokens(phrase);
                }
                if (matched == null) {
                    matched = matchEntityByPhrase(phrase, 0.8);
                }
                if (matched == null) {
                    matched = matchEntityByOntologyLabel(phrase);
                }
                if (matched != null) {
                    mentions.add(matched);
                }
            }
        }
        return new ArrayList<>(mentions);
    }

    private List<String> splitMentionPhrases(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        normalized = normalized.replace("relationship", "").replace("between", "");
        normalized = normalized.replace("the", "").replace("a", "").replace("an", "");
        String[] parts = normalized.split("[,]");
        List<String> phrases = new ArrayList<>();
        for (String part : parts) {
            String[] andSplit = part.split("\\band\\b");
            for (String piece : andSplit) {
                String trimmed = piece.trim();
                if (!trimmed.isBlank()) {
                    phrases.add(trimmed);
                }
            }
        }
        return phrases;
    }

    private List<String> relationshipPhrasesFromInput(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        int betweenIdx = normalized.indexOf("between");
        if (betweenIdx >= 0) {
            normalized = normalized.substring(betweenIdx + "between".length());
        }
        return splitMentionPhrases(normalized);
    }

    private Set<SymbolId> surfaceMentionFallback(String text, int limit) {
        LinkedHashSet<SymbolId> matches = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return matches;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (EntityNode entity : graph.getAllEntities()) {
            String surface = entity.surfaceForm();
            if (surface == null || surface.isBlank()) {
                continue;
            }
            String surfaceNorm = surface.toLowerCase(Locale.ROOT).replace('_', ' ');
            if (surfaceNorm.length() < 4 || isGenericMentionToken(surfaceNorm)) {
                continue;
            }
            if (normalized.contains(surfaceNorm)) {
                matches.add(entity.id());
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }

    private SymbolId matchEntityByOntologyLabel(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        String normalized = phrase.trim().toLowerCase(Locale.ROOT);
        for (String iri : annotationResolver.entityIrisByLabel(normalized)) {
            SymbolId iriId = new SymbolId(iri);
            if (isKnownSymbol(iriId)) {
                return iriId;
            }
            for (String label : annotationResolver.labelsForIri(iri)) {
                String token = annotationResolver.normalizeLabelToToken(label);
                if (token.isBlank()) {
                    continue;
                }
                SymbolId entity = new SymbolId("entity:" + token);
                if (isKnownSymbol(entity)) {
                    return entity;
                }
                SymbolId concept = new SymbolId("concept:" + token);
                if (isKnownSymbol(concept)) {
                    return concept;
                }
            }
        }
        return null;
    }

    private SymbolId matchEntityByPhrase(String phrase, double minScore) {
        String normalized = phrase.trim().replaceAll("[^a-z0-9_\\s-]", "");
        normalized = normalized.replace('-', '_').replace(' ', '_');
        if (!normalized.isBlank()) {
            SymbolId direct = new SymbolId("entity:" + normalized);
            if (graph.findEntity(direct).isPresent()) {
                return direct;
            }
            SymbolId concept = new SymbolId("concept:" + normalized);
            if (graph.findEntity(concept).isPresent()) {
                return concept;
            }
            SymbolId controlFallback = matchControlNounFallback(normalized);
            if (controlFallback != null) {
                return controlFallback;
            }
        }
        String phraseCompact = normalized.replace('_', ' ').trim();
        String singular = phraseCompact.endsWith("s") ? phraseCompact.substring(0, phraseCompact.length() - 1) : phraseCompact;
        SymbolId best = null;
        double bestScore = 0.0;
        for (EntityNode entity : graph.getAllEntities()) {
            String surface = entity.surfaceForm().toLowerCase(Locale.ROOT).replace('_', ' ');
            String idValue = entity.id().value().toLowerCase(Locale.ROOT).replace('_', ' ');
            double score = matchScore(phraseCompact, singular, surface, idValue);
            if (score > bestScore) {
                bestScore = score;
                best = entity.id();
            }
        }
        return bestScore >= minScore ? best : null;
    }

    private SymbolId matchEntityBySurfaceTokens(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        refreshIndexesIfNeeded();
        String normalized = normalizeInput(phrase);
        Set<SymbolId> candidates = findEntitiesByTokens(normalized);
        if (candidates.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId id : candidates) {
            if (!inputMentionsEntity(normalized, id)) {
                continue;
            }
            double score = answerRanker.specificityScore(id.value());
            if (score > bestScore) {
                bestScore = score;
                best = id;
            }
        }
        return best;
    }

    private SymbolId matchEntityByPhraseExact(String phrase) {
        String normalized = phrase.trim().replaceAll("[^a-z0-9_\\s-]", "");
        normalized = normalized.replace('-', '_').replace(' ', '_');
        if (normalized.isBlank()) {
            return null;
        }
        SymbolId controlFallback = matchControlNounFallback(normalized);
        if (controlFallback != null) {
            return controlFallback;
        }
        String singular = normalized.endsWith("s") ? normalized.substring(0, normalized.length() - 1) : normalized;
        SymbolId entity = new SymbolId("entity:" + normalized);
        if (isKnownSymbol(entity)) {
            return entity;
        }
        SymbolId singularEntity = new SymbolId("entity:" + singular);
        if (isKnownSymbol(singularEntity)) {
            return singularEntity;
        }
        SymbolId concept = new SymbolId("concept:" + normalized);
        if (isKnownSymbol(concept)) {
            return concept;
        }
        SymbolId singularConcept = new SymbolId("concept:" + singular);
        if (isKnownSymbol(singularConcept)) {
            return singularConcept;
        }
        return null;
    }

    private SymbolId matchControlNounFallback(String normalizedToken) {
        if (normalizedToken == null || normalizedToken.isBlank()) {
            return null;
        }
        String trimmed = null;
        if (normalizedToken.endsWith("_control") && normalizedToken.length() > "_control".length()) {
            trimmed = normalizedToken.substring(0, normalizedToken.length() - "_control".length());
        } else if (normalizedToken.startsWith("control_") && normalizedToken.length() > "control_".length()) {
            trimmed = normalizedToken.substring("control_".length());
        }
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }
        SymbolId entity = new SymbolId("entity:" + trimmed);
        if (isKnownSymbol(entity)) {
            return entity;
        }
        SymbolId concept = new SymbolId("concept:" + trimmed);
        if (isKnownSymbol(concept)) {
            return concept;
        }
        return null;
    }

    private SymbolId fallbackEntityFromPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        String normalized = phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\s-]", "");
        normalized = normalized.replace('-', '_');
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            if (part.isBlank() || part.length() < 4) {
                continue;
            }
            if (isGenericMentionToken(part)) {
                continue;
            }
            SymbolId found = findEntityByToken(part);
            if (found != null) {
                return found;
            }
            if (part.endsWith("s")) {
                found = findEntityByToken(part.substring(0, part.length() - 1));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isGenericMentionToken(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String normalized = token.toLowerCase(Locale.ROOT).replace('_', ' ');
        return switch (normalized) {
            case "system", "systems", "component", "components", "actuator", "actuators",
                    "control", "controls" -> true;
            default -> false;
        };
    }

    private SymbolId findEntityByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        refreshIndexesIfNeeded();
        String target = token.toLowerCase(Locale.ROOT);
        Set<SymbolId> matches = labelTokenIndex.get(target);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId candidate : matches) {
            double score = answerRanker.specificityScore(candidate.value());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean isKnownSymbol(SymbolId id) {
        if (id == null) {
            return false;
        }
        if (graph.findEntity(id).isPresent()) {
            return true;
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (id.equals(assertion.subject()) || id.equals(assertion.object())) {
                return true;
            }
        }
        return false;
    }

    private double matchScore(String phrase, String singular, String surface, String idValue) {
        double score = 0.0;
        if (surface.equals(phrase) || idValue.equals(phrase)) {
            score += 2.0;
        } else if (surface.equals(singular) || idValue.equals(singular)) {
            score += 1.6;
        }
        if (surface.contains(phrase) || idValue.contains(phrase)) {
            score += 0.8;
        }
        if (surface.contains(singular) || idValue.contains(singular)) {
            score += 0.6;
        }
        return score;
    }

    private String ruledOutCauseAnswer(QueryGoal goal, SymbolId target) {
        ExplanationCandidate candidate = explanationChains.buildExplanationCandidate(target, 4);
        if (candidate == null) {
            return null;
        }
        List<SymbolId> failures = new ArrayList<>(candidate.componentFailures());
        failures.addAll(candidate.subsystemFailures());
        if (failures.isEmpty()) {
            return null;
        }
        double bestScore = 0.0;
        for (SymbolId failure : failures) {
            if (failure == null || answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            bestScore = Math.max(bestScore, evidenceAlignmentScore(candidate, failure, target, goal));
            if (hasTemporalContext(failure)) {
                bestScore = Math.max(bestScore, 0.4);
            }
        }
        if (bestScore >= 0.3) {
            return "No causes ruled out.";
        }
        LinkedHashSet<String> ruledOut = new LinkedHashSet<>();
        for (SymbolId failure : failures) {
            if (failure == null || answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            double score = evidenceAlignmentScore(candidate, failure, target, goal);
            if (score < 0.2) {
                ruledOut.add(failure.value());
            }
        }
        if (ruledOut.isEmpty()) {
            return "No causes ruled out.";
        }
        List<String> limited = new ArrayList<>(ruledOut);
        if (limited.size() > 3) {
            limited = limited.subList(0, 3);
        }
        return String.join(", ", limited);
    }

    private String dependencyContrastAnswer(QueryGoal goal) {
        List<SymbolId> mentions = extractMentionedEntities(goal);
        if (mentions.isEmpty()) {
            return null;
        }
        List<String> sentences = new ArrayList<>();
        List<String> recoveryEvidence = explanationChains.buildRecoveryEvidence(2);
        sentences.addAll(recoveryEvidence);
        LinkedHashSet<String> dependencySentences = new LinkedHashSet<>();
        List<SymbolId> mentionAliases = new ArrayList<>();
        for (SymbolId mention : mentions) {
            mentionAliases.addAll(aliasBridge.expandAliasSymbols(mention));
        }
        LinkedHashSet<SymbolId> typedSubjects = new LinkedHashSet<>();
        for (EntityNode entity : graph.getAllEntities()) {
            if (entityMatchesMentionType(entity, mentionAliases)) {
                typedSubjects.add(entity.id());
            }
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!PREDICATE_TYPE.equals(assertion.predicate())) {
                continue;
            }
            if (mentionAliases.contains(assertion.object())) {
                typedSubjects.add(assertion.subject());
            }
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
            if (!"poweredby".equals(predicate) && !"require".equals(predicate) && !"requires".equals(predicate)) {
                continue;
            }
            if (mentionAliases.contains(assertion.subject()) || typedSubjects.contains(assertion.subject())) {
                dependencySentences.add(answerRenderer.formatAssertionSentence(assertion));
            }
        }
        if (sentences.isEmpty() && dependencySentences.isEmpty()) {
            return null;
        }
        for (String entry : dependencySentences) {
            sentences.add(entry);
            if (sentences.size() >= 4) {
                break;
            }
        }
        if (!recoveryEvidence.isEmpty() && !dependencySentences.isEmpty()) {
            String mentionLabel = displayValue(mentions.get(0));
            sentences.add("This suggests the recovery likely did not depend on " + mentionLabel + ".");
        }
        return String.join("\n", sentences);
    }

    private String conditionContrastAnswer(QueryGoal goal) {
        List<SymbolId> mentions = extractMentionedEntities(goal);
        if (mentions.size() < 2) {
            return conditionContrastByResources(goal);
        }
        SymbolId failing = mentions.get(0);
        SymbolId surviving = mentions.get(1);
        SymbolId failResource = firstDependencyResource(failing);
        SymbolId surviveResource = firstDependencyResource(surviving);
        if (failResource == null || surviveResource == null) {
            return null;
        }
        return "If " + displayValue(failResource) + " is unavailable but "
                + displayValue(surviveResource) + " remains available, then "
                + displayValue(failing) + " may fail while "
                + displayValue(surviving) + " can operate.";
    }

    String relationMatchFallbackAnswer(QueryGoal goal) {
        if (wantsDependencyContrast(goal)) {
            String contrast = dependencyContrastAnswer(goal);
            if (contrast != null) {
                return contrast;
            }
        }
        if (wantsConditionContrast(goal)) {
            String survivors = contrastiveSurvivorAnswer(goal);
            if (survivors != null) {
                return survivors;
            }
            String contrast = conditionContrastAnswer(goal);
            if (contrast != null) {
                return contrast;
            }
        }
        String dependencyList = dependencyFailureListAnswer(goal);
        if (dependencyList != null) {
            return dependencyList;
        }
        return null;
    }

    private String contrastiveSurvivorAnswer(QueryGoal goal) {
        List<SymbolId> resources = resourceMentions(goal);
        if (resources.size() < 2) {
            return null;
        }
        SymbolId failResource = null;
        SymbolId surviveResource = null;
        for (SymbolId resource : resources) {
            int status = resourceAvailability(resource);
            if (status < 0 && failResource == null) {
                failResource = resource;
            } else if (status > 0 && surviveResource == null) {
                surviveResource = resource;
            }
        }
        if (failResource == null || surviveResource == null) {
            return null;
        }
        List<SymbolId> failDependents = entitiesDependingOn(failResource);
        List<SymbolId> surviveDependents = entitiesDependingOn(surviveResource);
        if (surviveDependents.isEmpty()) {
            return null;
        }
        LinkedHashSet<SymbolId> survivors = new LinkedHashSet<>();
        for (SymbolId candidate : surviveDependents) {
            if (candidate == null) {
                continue;
            }
            if (failDependents.contains(candidate)) {
                continue;
            }
            if (!matchesExpectedType(candidate, goal)) {
                continue;
            }
            survivors.add(candidate);
        }
        if (survivors.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (SymbolId survivor : survivors) {
            values.add(survivor.value());
        }
        return renderEntityList(values, goal);
    }

    private boolean matchesExpectedType(SymbolId candidate, QueryGoal goal) {
        if (candidate == null || goal == null) {
            return true;
        }
        String expected = goal.expectedType();
        if (expected == null || expected.isBlank()) {
            expected = goal.entityType();
        }
        if (expected == null || expected.isBlank()) {
            return true;
        }
        EntityNode entity = graph.findEntity(candidate).orElse(null);
        if (entity == null) {
            return true;
        }
        Set<String> types = entity.conceptTypes();
        if (types == null || types.isEmpty()) {
            return true;
        }
        String normalizedExpected = support.normalizeTypeToken(expected);
        for (String type : types) {
            if (type.equals(expected)) {
                return true;
            }
            if (support.normalizeTypeToken(type).equals(normalizedExpected)) {
                return true;
            }
            if (ontology.isSubclassOf(type, expected)) {
                return true;
            }
        }
        return false;
    }

    private String conditionContrastByResources(QueryGoal goal) {
        List<SymbolId> resources = resourceMentions(goal);
        if (resources.size() < 2) {
            return null;
        }
        SymbolId failResource = null;
        SymbolId surviveResource = null;
        for (SymbolId resource : resources) {
            int status = resourceAvailability(resource);
            if (status < 0 && failResource == null) {
                failResource = resource;
            } else if (status > 0 && surviveResource == null) {
                surviveResource = resource;
            }
        }
        if (failResource == null || surviveResource == null) {
            failResource = resources.get(0);
            surviveResource = resources.get(1);
        }
        List<SymbolId> failDependents = entitiesDependingOn(failResource);
        List<SymbolId> surviveDependents = entitiesDependingOn(surviveResource);
        SymbolId surviving = selectBestSpecific(surviveDependents, true);
        if (surviving == null) {
            return null;
        }
        SymbolId failing = null;
        for (SymbolId candidate : failDependents) {
            if (candidate != null && !candidate.equals(surviving)) {
                failing = candidate;
                break;
            }
        }
        if (failing == null) {
            return null;
        }
        return "If " + displayValue(failResource) + " is unavailable but "
                + displayValue(surviveResource) + " remains available, then "
                + displayValue(failing) + " may fail while "
                + displayValue(surviving) + " can operate.";
    }

    private int resourceAvailability(SymbolId resource) {
        if (resource == null) {
            return 0;
        }
        int status = resourceAvailabilityFromAssertions(resource);
        if (status != 0) {
            return status;
        }
        return resourceAvailabilityFromInput(resource);
    }

    private int resourceAvailabilityFromAssertions(SymbolId resource) {
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!resource.equals(assertion.subject())) {
                continue;
            }
            String predicate = support.localName(assertion.predicate());
            if ("available".equals(predicate)) {
                return 1;
            }
            if ("unavailable".equals(predicate)) {
                return -1;
            }
            if ("hasattribute".equals(predicate) || "hasAttribute".equals(predicate)) {
                if (hasSemanticRole(assertion.object(), "resource_available")) {
                    return 1;
                }
                if (hasSemanticRole(assertion.object(), "resource_unavailable")) {
                    return -1;
                }
            }
        }
        return 0;
    }

    private int resourceAvailabilityFromInput(SymbolId resource) {
        String input = support.lastInput();
        if (input == null || input.isBlank()) {
            return 0;
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        Map<SymbolId, List<StateMarker>> assignments = assignStateMarkersToResources(normalized);
        List<StateMarker> markers = assignments.get(resource);
        if (markers.isEmpty()) {
            return 0;
        }
        int bestStatus = 0;
        for (StateMarker marker : markers) {
            bestStatus = marker.status();
            if (bestStatus != 0) {
                break;
            }
        }
        return bestStatus;
    }

    private record StateMarker(int position, int status) {}

    private List<StateMarker> stateMarkers(String input) {
        List<StateMarker> markers = new ArrayList<>();
        markers.addAll(stateMarkersForRole(input, "resource_available", 1));
        markers.addAll(stateMarkersForRole(input, "resource_unavailable", -1));
        return markers;
    }

    private List<StateMarker> stateMarkersForRole(String input, String role, int status) {
        List<StateMarker> markers = new ArrayList<>();
        Set<String> entities = annotationResolver.entitiesWithAnnotation(
                com.sahr.ontology.SahrAnnotationVocabulary.SEMANTIC_ROLE,
                role
        );
        for (String iri : entities) {
            for (String label : annotationResolver.labelsForIri(iri)) {
                for (int pos : labelPositions(input, label)) {
                    markers.add(new StateMarker(pos, status));
                }
            }
        }
        return markers;
    }

    private Map<SymbolId, List<StateMarker>> assignStateMarkersToResources(String input) {
        Map<SymbolId, List<StateMarker>> assignments = new HashMap<>();
        if (input == null || input.isBlank()) {
            return assignments;
        }
        Map<SymbolId, List<Integer>> resourcePositions = resourceLabelPositions(input);
        List<StateMarker> markers = stateMarkers(input);
        for (Map.Entry<SymbolId, List<Integer>> entry : resourcePositions.entrySet()) {
            assignments.put(entry.getKey(), new ArrayList<>());
        }
        for (StateMarker marker : markers) {
            SymbolId nearest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Map.Entry<SymbolId, List<Integer>> entry : resourcePositions.entrySet()) {
                for (int pos : entry.getValue()) {
                    int distance = Math.abs(pos - marker.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = entry.getKey();
                    }
                }
            }
            if (nearest != null) {
                assignments.computeIfAbsent(nearest, key -> new ArrayList<>()).add(marker);
            }
        }
        return assignments;
    }

    private Map<SymbolId, List<Integer>> resourceLabelPositions(String input) {
        Map<SymbolId, List<Integer>> positions = new HashMap<>();
        for (EntityNode entity : graph.getAllEntities()) {
            SymbolId id = entity.id();
            if (!hasSemanticRole(id, "resource")) {
                continue;
            }
            List<Integer> resourcePositions = labelPositions(input, resourceLabels(id));
            if (!resourcePositions.isEmpty()) {
                positions.put(id, resourcePositions);
            }
        }
        return positions;
    }

    private List<String> resourceLabels(SymbolId resource) {
        List<String> labels = new ArrayList<>();
        labels.add(displayValue(resource));
        String iri = resolveEntityIri(resource);
        if (iri != null) {
            labels.addAll(annotationResolver.labelsForIri(iri));
        }
        return labels;
    }

    private List<String> labelsForSymbol(SymbolId id) {
        List<String> labels = new ArrayList<>();
        labels.add(displayValue(id));
        String iri = resolveEntityIri(id);
        if (iri != null) {
            labels.addAll(annotationResolver.labelsForIri(iri));
        }
        return labels;
    }

    private List<Integer> labelPositions(String input, List<String> labels) {
        List<Integer> positions = new ArrayList<>();
        if (labels == null) {
            return positions;
        }
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            positions.addAll(labelPositions(input, label));
        }
        return positions;
    }

    private List<Integer> labelPositions(String input, String label) {
        List<Integer> positions = new ArrayList<>();
        if (input == null || input.isBlank() || label == null || label.isBlank()) {
            return positions;
        }
        String normalized = label.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return positions;
        }
        int index = input.indexOf(normalized);
        while (index >= 0) {
            positions.add(index);
            index = input.indexOf(normalized, index + normalized.length());
        }
        String token = annotationResolver.normalizeLabelToToken(normalized).replace('_', ' ');
        if (!token.isBlank() && !token.equals(normalized)) {
            index = input.indexOf(token);
            while (index >= 0) {
                positions.add(index);
                index = input.indexOf(token, index + token.length());
            }
        }
        return positions;
    }

    private SymbolId firstDependencyResource(SymbolId entity) {
        if (entity == null) {
            return null;
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!assertion.subject().equals(entity)) {
                continue;
            }
            String predicate = support.localName(assertion.predicate());
            if ("poweredby".equals(predicate) || "require".equals(predicate) || "requires".equals(predicate)) {
                return assertion.object();
            }
        }
        return null;
    }

    private String dependencyFailureListAnswer(QueryGoal goal) {
        if (goal == null) {
            return null;
        }
        if (!wantsDependencyList(goal)) {
            return null;
        }
        String dependencyKey = buildGoalCacheKey("dependency_failure", goal);
        String cached = dependencyFailureCache.get(dependencyKey);
        if (cached != null) {
            return cached;
        }
        List<SymbolId> resources = resourceMentions(goal);
        if (resources.isEmpty()) {
            return null;
        }
        LinkedHashSet<SymbolId> dependents = new LinkedHashSet<>();
        for (SymbolId resource : resources) {
            dependents.addAll(entitiesDependingOn(resource));
        }
        if (dependents.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (SymbolId dependent : dependents) {
            values.add(dependent.value());
        }
        String rendered = renderEntityList(values, goal);
        dependencyFailureCache.put(dependencyKey, rendered);
        return rendered;
    }

    private List<SymbolId> resourceMentions(QueryGoal goal) {
        List<SymbolId> mentions = extractMentionedEntities(goal);
        List<SymbolId> resources = new ArrayList<>();
        for (SymbolId mention : mentions) {
            if (hasSemanticRole(mention, "resource")) {
                resources.add(mention);
            }
        }
        if (!resources.isEmpty()) {
            return resources;
        }
        String input = support.lastInput();
        if (input == null || input.isBlank()) {
            return resources;
        }
        if (!containsCue(input, "power", "propellant", "resource", "battery", "fuel", "electric", "supply",
                "dependency", "depend", "requires", "require")) {
            return resources;
        }
        refreshIndexesIfNeeded();
        String normalizedInput = normalizeInput(input);
        Set<SymbolId> candidates = findEntitiesByTokens(normalizedInput);
        for (SymbolId id : candidates) {
            if (!hasSemanticRole(id, "resource")) {
                continue;
            }
            if (inputMentionsEntity(normalizedInput, id)) {
                resources.add(id);
            }
        }
        return resources;
    }

    private boolean wantsDependencyList(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        String predicate = support.localName(goal.predicate());
        if ("poweredby".equals(predicate) || "require".equals(predicate) || "requires".equals(predicate)) {
            return true;
        }
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null) {
                continue;
            }
            String normalized = fragment.toLowerCase(Locale.ROOT);
            if (normalized.contains("depend") || normalized.contains("dependency")
                    || normalized.contains("resource") || normalized.contains("power")
                    || normalized.contains("propellant") || normalized.contains("battery")
                    || normalized.contains("fuel")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "depend", "dependency", "resource", "power",
                "propellant", "battery", "fuel", "electric", "requires", "require");
    }

    private boolean inputMentionsEntity(String normalizedInput, SymbolId id) {
        if (normalizedInput == null || normalizedInput.isBlank() || id == null) {
            return false;
        }
        List<String> phrases = labelPhrasesByEntity.get(id);
        if (phrases == null || phrases.isEmpty()) {
            return false;
        }
        for (String phrase : phrases) {
            if (phrase.isBlank()) {
                continue;
            }
            if (normalizedInput.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private List<SymbolId> entitiesDependingOn(SymbolId resource) {
        List<SymbolId> dependents = new ArrayList<>();
        if (resource == null) {
            return dependents;
        }
        refreshIndexesIfNeeded();
        Set<SymbolId> direct = dependentsByResource.get(resource);
        if (direct != null) {
            dependents.addAll(direct);
        }
        return dependents;
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
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null) {
                continue;
            }
            String normalized = fragment.toLowerCase(Locale.ROOT);
            if (normalized.contains("chain") || normalized.contains("explain")
                    || normalized.contains("why") || normalized.contains("most likely")
                    || normalized.contains("most directly") || normalized.contains("best fits")
                    || normalized.contains("plausible")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "chain", "explain", "why", "most likely", "most directly", "best fits", "plausible");
    }

    private String bestFailureChainToOutcome(ExplanationCandidate candidate, SymbolId outcome, QueryGoal goal) {
        if (candidate == null) {
            return null;
        }
        List<SymbolId> failures = new ArrayList<>(candidate.componentFailures());
        failures.addAll(candidate.subsystemFailures());
        if (failures.isEmpty()) {
            return null;
        }
        ForwardChainSearch.ChainResult best = null;
        for (SymbolId failure : failures) {
            if (failure == null) {
                continue;
            }
            ForwardChainSearch.ChainResult result = forwardChainSearch.search(failure, outcome, 3);
            if (result == null || result.sentences().isEmpty()) {
                continue;
            }
            double score = result.score() + evidenceAlignmentScore(candidate, failure, outcome, goal);
            if (best == null || score > best.score()) {
                best = new ForwardChainSearch.ChainResult(result.sentences(), score);
            }
        }
        return best == null ? null : String.join("\n", best.sentences());
    }

    private String evidenceSignalAnswer(QueryGoal goal) {
        if (!wantsEvidenceSignal(goal)) {
            return null;
        }
        if (wantsChainExplanation(goal) || wantsEvidenceAlignedChain(goal)) {
            return null;
        }
        String cacheKey = buildGoalCacheKey("evidence_signal", goal);
        String cached = evidenceSignalCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        SymbolId signal = selectEvidenceSignalFromInput();
        if (signal == null) {
            signal = selectEvidenceSignalFromGraph();
        }
        if (signal == null) {
            SymbolId outcome = selectPrimaryOutcomeCandidate();
            ExplanationCandidate candidate = outcome == null ? null : explanationCandidate(outcome, 4);
            signal = selectBestEvidenceSignal(candidate);
        }
        String rendered = signal == null ? null : renderEntityAnswer(signal.value(), goal, AnswerRole.EVIDENCE_SIGNAL);
        if (rendered != null) {
            evidenceSignalCache.put(cacheKey, rendered);
        }
        return rendered;
    }

    String evidenceSignalAnswerIfApplicable(QueryGoal goal) {
        return evidenceSignalAnswer(goal);
    }

    private boolean wantsEvidenceSignal(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        if (expectsTypeLabel(goal, "signal") || expectsTypeLabel(goal, "telemetry")) {
            return true;
        }
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null) {
                continue;
            }
            String normalized = fragment.toLowerCase(Locale.ROOT);
            if (normalized.contains("signal") || normalized.contains("telemetry")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "signal", "telemetry");
    }

    private SymbolId selectBestEvidenceSignal(ExplanationCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        List<SymbolId> candidates = new ArrayList<>();
        candidates.addAll(candidate.precursorSignals());
        candidates.addAll(candidate.evidenceNodes());
        SymbolId best = null;
        double bestScore = -1.0;
        for (SymbolId signal : candidates) {
            if (signal == null) {
                continue;
            }
            if (!hasSemanticRole(signal, "evidence_signal")) {
                continue;
            }
            double score = answerRanker.specificityScore(signal.value());
            if (best == null || score > bestScore) {
                best = signal;
                bestScore = score;
            }
        }
        return best;
    }

    private SymbolId selectEvidenceSignalFromInput() {
        String input = support.lastInput();
        if (input == null || input.isBlank()) {
            return null;
        }
        refreshIndexesIfNeeded();
        String normalizedInput = normalizeInput(input);
        SymbolId best = null;
        double bestScore = -1.0;
        Set<SymbolId> candidates = findEntitiesByTokens(normalizedInput);
        for (SymbolId id : candidates) {
            if (!hasSemanticRole(id, "evidence_signal")) {
                continue;
            }
            if (!inputMentionsEntity(normalizedInput, id)) {
                continue;
            }
            double score = answerRanker.specificityScore(id.value());
            if (best == null || score > bestScore) {
                best = id;
                bestScore = score;
            }
        }
        return best;
    }

    private SymbolId selectEvidenceSignalFromGraph() {
        refreshIndexesIfNeeded();
        SymbolId best = null;
        double bestScore = -1.0;
        for (Map.Entry<SymbolId, Double> entry : evidenceSignalWeightBySubject.entrySet()) {
            SymbolId subject = entry.getKey();
            double score = entry.getValue() + answerRanker.specificityScore(subject.value());
            if (best == null || score > bestScore) {
                best = subject;
                bestScore = score;
            }
        }
        return best;
    }

    private double predicateEvidenceWeight(String predicate) {
        return predicateEvidenceWeightCached(predicate);
    }

    private double predicateEvidenceWeightCached(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return 0.0;
        }
        Double cached = evidencePredicateWeight.get(predicate);
        if (cached != null) {
            return cached;
        }
        double weight = annotationResolver.resolveObjectPropertyIri(predicate)
                .flatMap(iri -> annotationResolver.annotationDouble(iri, com.sahr.ontology.SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                .orElse(0.0);
        evidencePredicateWeight.put(predicate, weight);
        return weight;
    }

    private SymbolId selectPrimaryOutcomeCandidate() {
        List<SymbolId> outcomes = collectOutcomeCandidates();
        if (outcomes.isEmpty()) {
            return null;
        }
        return selectBestSpecific(outcomes, false);
    }

    private List<SymbolId> collectOutcomeCandidates() {
        LinkedHashSet<SymbolId> outcomes = new LinkedHashSet<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
            if ("cause".equals(predicate)) {
                if (hasSemanticRole(assertion.object(), "outcome")) {
                    outcomes.add(assertion.object());
                }
            } else if ("causedby".equals(predicate)) {
                if (hasSemanticRole(assertion.subject(), "outcome")) {
                    outcomes.add(assertion.subject());
                }
            }
        }
        return new ArrayList<>(outcomes);
    }

    private SymbolId resolveOutcomeTarget(ExplanationCandidate candidate, SymbolId target, QueryGoal goal) {
        if (target == null) {
            return null;
        }
        if (hasSemanticRole(target, "control_target") || hasSemanticRole(target, "outcome")) {
            return target;
        }
        if (hasSemanticRole(target, "evidence_signal") || hasSemanticRole(target, "temporal_marker")) {
            SymbolId outcome = selectPrimaryOutcomeCandidate();
            return outcome == null ? target : outcome;
        }
        if (answerRanker.isGenericLossValue(target.value())) {
            SymbolId outcome = selectPrimaryOutcomeCandidate();
            return outcome == null ? target : outcome;
        }
        if (wantsChainExplanation(goal) || wantsEvidenceAlignedChain(goal)) {
            SymbolId outcome = selectPrimaryOutcomeCandidate();
            if (outcome != null) {
                return outcome;
            }
        }
        return target;
    }

    private double evidenceAlignmentScore(ExplanationCandidate candidate,
                                          SymbolId failure,
                                          SymbolId outcome,
                                          QueryGoal goal) {
        String alignmentKey = buildEvidenceAlignmentKey(candidate, failure, outcome, goal);
        Double cachedAlignment = evidenceAlignmentCache.get(alignmentKey);
        if (cachedAlignment != null) {
            return cachedAlignment;
        }
        double score = 0.0;
        List<SymbolId> precursorSignals = candidate.precursorSignals();
        for (SymbolId signal : precursorSignals) {
            if (signal == null) {
                continue;
            }
            score += temporalSupportScore(signal, failure) * 0.6;
            if (outcome != null) {
                score += temporalSupportScore(signal, outcome) * 0.4;
            }
        }
        List<SymbolId> evidence = candidate.evidenceNodes();
        for (SymbolId ev : evidence) {
            if (ev == null) {
                continue;
            }
            score += temporalSupportScore(ev, failure) * 0.4;
            if (outcome != null) {
                score += temporalSupportScore(ev, outcome) * 0.2;
            }
        }
        if (goal != null && goal.modifier() != null && goal.modifier().toLowerCase(Locale.ROOT).contains("sequence")) {
            score += 0.2;
        }
        evidenceAlignmentCache.put(alignmentKey, score);
        return score;
    }

    private double temporalSupportScore(SymbolId cause, SymbolId effect) {
        if (cause == null || effect == null) {
            return 0.0;
        }
        String temporalKey = buildTemporalSupportKey(cause, effect);
        Double cachedTemporal = temporalSupportCache.get(temporalKey);
        if (cachedTemporal != null) {
            return cachedTemporal;
        }
        double score = 0.0;
        refreshIndexesIfNeeded();
        for (RelationAssertion assertion : assertionsByLocalPredicate("before", "after", "during")) {
            String predicate = support.localName(assertion.predicate());
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
        temporalSupportCache.put(temporalKey, score);
        return score;
    }

    private String buildTemporalSupportKey(SymbolId cause, SymbolId effect) {
        return cause.value() + "->" + effect.value();
    }

    private String buildEvidenceAlignmentKey(ExplanationCandidate candidate,
                                             SymbolId failure,
                                             SymbolId outcome,
                                             QueryGoal goal) {
        String candidateId = candidate == null ? "none" : String.valueOf(System.identityHashCode(candidate));
        String failureKey = failure == null ? "none" : failure.value();
        String outcomeKey = outcome == null ? "none" : outcome.value();
        String sequence = (goal != null && goal.modifier() != null
                && goal.modifier().toLowerCase(Locale.ROOT).contains("sequence")) ? "1" : "0";
        return candidateId + "|" + failureKey + "|" + outcomeKey + "|" + sequence;
    }

    private String buildGoalCacheKey(String prefix, QueryGoal goal) {
        String subject = goal == null ? "" : safe(goal.subject());
        String predicate = goal == null ? "" : safe(goal.predicate());
        String object = goal == null ? "" : safe(goal.object());
        String modifier = goal == null ? "" : safe(goal.modifier());
        String expected = goal == null ? "" : safe(goal.expectedType());
        String entityType = goal == null ? "" : safe(goal.entityType());
        String type = goal == null || goal.type() == null ? "" : goal.type().name();
        String input = safe(support.lastInput());
        return prefix + "|" + type + "|" + subject + "|" + predicate + "|" + object
                + "|" + modifier + "|" + expected + "|" + entityType + "|" + input;
    }

    private String buildStructuredChainKey(ExplanationCandidate candidate,
                                           QueryGoal goal,
                                           SymbolId target,
                                           String predicate,
                                           boolean evidenceAligned) {
        String candidateId = candidate == null ? "none" : String.valueOf(System.identityHashCode(candidate));
        String targetKey = target == null ? "" : target.value();
        String pred = predicate == null ? "" : predicate;
        String goalKey = buildGoalCacheKey("structured_chain", goal);
        return candidateId + "|" + goalKey + "|" + targetKey + "|" + pred + "|" + (evidenceAligned ? "1" : "0");
    }

    private ExplanationCandidate explanationCandidate(SymbolId target, int depth) {
        if (target == null) {
            return null;
        }
        String key = target.value() + "|" + depth;
        ExplanationCandidate cached = explanationCandidateCache.get(key);
        if (cached != null) {
            return cached;
        }
        ExplanationCandidate candidate = explanationChains.buildExplanationCandidate(target, depth);
        if (candidate != null) {
            explanationCandidateCache.put(key, candidate);
        }
        return candidate;
    }

    private List<String> buildStructuredChain(ExplanationCandidate candidate,
                                              QueryGoal goal,
                                              SymbolId target,
                                              String predicate) {
        List<String> sentences = new ArrayList<>();
        if (candidate == null) {
            return sentences;
        }
        boolean timing = Boolean.parseBoolean(System.getProperty(TIMING_PROPERTY, "false"));
        boolean evidenceAligned = wantsEvidenceAlignedChain(goal)
                || "cause".equals(predicate)
                || "causedby".equals(predicate)
                || hasTemporalContext(target);
        String chainKey = buildStructuredChainKey(candidate, goal, target, predicate, evidenceAligned);
        List<String> cached = structuredChainCache.get(chainKey);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<SymbolId> componentFailures = new ArrayList<>(candidate.componentFailures());
        List<SymbolId> subsystemFailures = new ArrayList<>(candidate.subsystemFailures());
        if (target != null) {
            subsystemFailures.removeIf(target::equals);
        }
        if (componentFailures.size() > 200) {
            componentFailures = selectTopSpecific(componentFailures, 200, true, null);
        }
        if (subsystemFailures.size() > 200) {
            subsystemFailures = selectTopSpecific(subsystemFailures, 200, true, null);
        }
        SymbolId outcomeTarget = resolveOutcomeTarget(candidate, target, goal);
        if (timing) {
            String extra = "componentFailures=" + componentFailures.size()
                    + " subsystemFailures=" + subsystemFailures.size()
                    + " precursors=" + candidate.precursorSignals().size()
                    + " evidence=" + candidate.evidenceNodes().size()
                    + " losses=" + candidate.capabilityLosses().size()
                    + " sentences=" + candidate.sentences().size();
            logCauseTiming("structured_candidate_sizes", goal, 0L, extra);
        }
        if (componentFailures.isEmpty()
                && subsystemFailures.isEmpty()
                && candidate.precursorSignals().isEmpty()
                && candidate.evidenceNodes().isEmpty()
                && candidate.capabilityLosses().isEmpty()
                && candidate.sentences().isEmpty()) {
            List<String> fallback = new ArrayList<>();
            if (outcomeTarget != null) {
                fallback.add(normalizeOutcomeLine(outcomeTarget));
            }
            structuredChainCache.put(chainKey, List.copyOf(fallback));
            return fallback;
        }
        SymbolId precursor = selectBestSpecific(candidate.precursorSignals(), true);
        if (precursor != null) {
            sentences.add(roleSentence("Precursor signal", precursor));
        }
        SymbolId componentFailure = null;
        SymbolId subsystemFailure = null;
        if (evidenceAligned) {
            long selectFailureStart = timing ? System.nanoTime() : 0L;
            SymbolId bestFailure = selectEvidenceAlignedFailure(candidate, target, goal);
            if (timing) {
                logCauseTiming("structured_select_failure", goal, System.nanoTime() - selectFailureStart, null);
            }
            if (bestFailure != null && candidate.componentFailures().contains(bestFailure)) {
                componentFailure = bestFailure;
            } else {
                subsystemFailure = bestFailure;
            }
        }
        if (componentFailure == null) {
            componentFailure = selectBestSpecific(componentFailures, true);
        }
        List<SymbolId> failureChain = new ArrayList<>();
        if (componentFailures.isEmpty()) {
            List<SymbolId> subsystemTop = selectTopSpecific(subsystemFailures, 2, true, null);
            failureChain.addAll(subsystemTop);
        } else {
            List<SymbolId> componentTop = selectTopSpecific(componentFailures, 2, true, null);
            failureChain.addAll(componentTop);
            if (subsystemFailure == null) {
                subsystemFailure = selectBestSpecificExcluding(subsystemFailures, componentFailure, true);
            }
            if (subsystemFailure != null && !failureChain.contains(subsystemFailure)) {
                failureChain.add(subsystemFailure);
            }
        }
        appendFailureChain(sentences, failureChain);
        long expandStart = timing ? System.nanoTime() : 0L;
        List<String> expandedChain = expandFailureChainIfShort(sentences, precursor, failureChain, outcomeTarget);
        if (timing) {
            logCauseTiming("structured_expand_chain", goal, System.nanoTime() - expandStart, null);
        }
        if (expandedChain != null) {
            sentences.clear();
            sentences.addAll(expandedChain);
        }
        SymbolId capabilityLoss = selectBestSpecific(candidate.capabilityLosses(), false);
        if (capabilityLoss != null) {
            sentences.add(formatCapabilityLossSentence(capabilityLoss));
        }
        if (capabilityLoss == null && wantsControlLoss(goal, outcomeTarget)) {
            SymbolId fallbackLoss = outcomeTarget;
            if (fallbackLoss != null) {
                sentences.add(formatCapabilityLossSentence(fallbackLoss));
            }
        }
        if (outcomeTarget != null) {
            sentences.add(normalizeOutcomeLine(outcomeTarget));
        }
        if (shouldIncludeRecovery(goal, predicate, outcomeTarget) || wantsRecoveryClause(goal)) {
            long recoveryStart = timing ? System.nanoTime() : 0L;
            SymbolId agent = selectBestRecoveryAgent(candidate, outcomeTarget);
            if (timing) {
                logCauseTiming("structured_recovery_agent", goal, System.nanoTime() - recoveryStart, null);
            }
            if (agent != null) {
                sentences.add(formatRecoverySentence(agent, outcomeTarget));
            }
        }
        long evidenceStart = timing ? System.nanoTime() : 0L;
        appendEvidenceLines(candidate, componentFailure, subsystemFailure, outcomeTarget, evidenceAligned, sentences);
        if (timing) {
            logCauseTiming("structured_append_evidence", goal, System.nanoTime() - evidenceStart, null);
        }
        structuredChainCache.put(chainKey, List.copyOf(sentences));
        return sentences;
    }

    private List<SymbolId> selectTopSpecific(List<SymbolId> candidates,
                                             int limit,
                                             boolean avoidLoss,
                                             SymbolId exclude) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (limit <= 0) {
            return List.of();
        }
        record Scored(SymbolId id, double score) {}
        java.util.PriorityQueue<Scored> heap = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(Scored::score)
        );
        for (SymbolId candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (exclude != null && exclude.equals(candidate)) {
                continue;
            }
            if (avoidLoss && answerRanker.isGenericLossValue(candidate.value())) {
                continue;
            }
            double score = answerRanker.specificityScore(candidate.value());
            if (heap.size() < limit) {
                heap.add(new Scored(candidate, score));
            } else if (score > heap.peek().score()) {
                heap.poll();
                heap.add(new Scored(candidate, score));
            }
        }
        if (heap.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>(heap);
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        List<SymbolId> results = new ArrayList<>(scored.size());
        for (Scored entry : scored) {
            results.add(entry.id());
        }
        return results;
    }

    private SymbolId selectBestSpecific(List<SymbolId> candidates, boolean avoidLoss) {
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

    private SymbolId selectBestSpecificExcluding(List<SymbolId> candidates,
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

    private SymbolId selectBestFailure(ExplanationCandidate candidate, boolean preferComponent) {
        if (candidate == null) {
            return null;
        }
        List<SymbolId> failures = new ArrayList<>();
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

    private SymbolId selectEvidenceAlignedFailure(ExplanationCandidate candidate, SymbolId outcome, QueryGoal goal) {
        if (candidate == null) {
            return null;
        }
        List<SymbolId> failures = new ArrayList<>();
        failures.addAll(candidate.componentFailures());
        failures.addAll(candidate.subsystemFailures());
        if (failures.isEmpty()) {
            return null;
        }
        List<SymbolId> scoredFailures = failures;
        if (failures.size() > 25) {
            scoredFailures = selectTopSpecific(failures, 25, false, null);
        }
        SymbolId best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SymbolId failure : scoredFailures) {
            if (failure == null || answerRanker.isGenericLossValue(failure.value())) {
                continue;
            }
            double score = evidenceAlignmentScore(candidate, failure, outcome, goal)
                    + answerRanker.specificityScore(failure.value()) * 0.3;
            if (hasTemporalContext(failure)) {
                score += 0.4;
            }
            if (outcome != null && hasTemporalContext(outcome)) {
                score += 0.2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = failure;
            }
        }
        return best != null ? best : selectBestSpecific(failures, true);
    }

    private void appendEvidenceLines(ExplanationCandidate candidate,
                                     SymbolId componentFailure,
                                     SymbolId subsystemFailure,
                                     SymbolId target,
                                     boolean evidenceAligned,
                                     List<String> sentences) {
        if (candidate == null) {
            return;
        }
        List<SymbolId> evidence = candidate.evidenceNodes();
        List<SymbolId> precursorSignals = candidate.precursorSignals();
        if (!precursorSignals.isEmpty()) {
            for (SymbolId node : precursorSignals) {
                if (node == null) {
                    continue;
                }
                sentences.add("Evidence: " + displayValue(node) + ".");
                if (sentences.size() >= 6) {
                    break;
                }
            }
        }
        if (!evidence.isEmpty()) {
            for (SymbolId node : evidence) {
                if (node == null) {
                    continue;
                }
                sentences.add("Evidence: " + displayValue(node) + ".");
                if (sentences.size() >= 6) {
                    break;
                }
            }
        }
        if (evidenceAligned && target != null && (componentFailure != null || subsystemFailure != null)) {
            SymbolId focus = componentFailure != null ? componentFailure : subsystemFailure;
            if (focus != null) {
                refreshIndexesIfNeeded();
                for (RelationAssertion assertion : assertionsByLocalPredicate("before", "after")) {
                    String predicate = support.localName(assertion.predicate());
                    if (assertion.subject().equals(focus) && assertion.object().equals(target)) {
                        sentences.add(answerRenderer.formatAssertionSentence(assertion));
                    } else if (assertion.subject().equals(target) && assertion.object().equals(focus)) {
                        sentences.add(answerRenderer.formatAssertionSentence(assertion));
                    }
                }
            }
        }
    }

    private boolean hasTemporalContext(SymbolId node) {
        if (node == null) {
            return false;
        }
        String key = node.value();
        Boolean cached = temporalContextCache.get(key);
        if (cached != null) {
            return cached;
        }
        refreshIndexesIfNeeded();
        boolean hasContext = temporalContextBySymbol.getOrDefault(node, false);
        temporalContextCache.put(key, hasContext);
        return hasContext;
    }

    private boolean wantsEvidenceAlignedChain(QueryGoal goal) {
        if (goal == null) {
            return false;
        }
        for (String fragment : goalTextFragments(goal)) {
            if (fragment == null) {
                continue;
            }
            String normalized = fragment.toLowerCase(Locale.ROOT);
            if (normalized.contains("sequence") || normalized.contains("evidence")) {
                return true;
            }
        }
        return false;
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

    private void appendFailureChain(List<String> sentences, List<SymbolId> failures) {
        if (failures == null || failures.isEmpty()) {
            return;
        }
        SymbolId first = failures.get(0);
        if (first != null) {
            sentences.add("The " + displayValue(first) + " failed.");
        }
        for (int i = 1; i < failures.size(); i++) {
            SymbolId failure = failures.get(i);
            if (failure == null) {
                continue;
            }
            sentences.add("This caused the " + displayValue(failure) + " to fail.");
        }
    }

    private List<String> expandFailureChainIfShort(List<String> sentences,
                                                   SymbolId precursor,
                                                   List<SymbolId> failures,
                                                   SymbolId outcomeTarget) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }
        if (sentences.size() > 2) {
            return null;
        }
        SymbolId firstFailure = failures.get(0);
        if (firstFailure == null || outcomeTarget == null) {
            return null;
        }
        ForwardChainSearch.ChainResult result = forwardChainSearch.search(firstFailure, outcomeTarget, 3);
        if (result == null || result.sentences() == null || result.sentences().size() <= sentences.size()) {
            return null;
        }
        List<String> expanded = new ArrayList<>();
        if (precursor != null) {
            expanded.add(roleSentence("Precursor signal", precursor));
        }
        expanded.addAll(result.sentences());
        return expanded;
    }

    private boolean entityMatchesMentionType(EntityNode entity, List<SymbolId> mentionAliases) {
        if (entity == null || mentionAliases == null || mentionAliases.isEmpty()) {
            return false;
        }
        Set<String> types = entity.conceptTypes();
        if (types == null || types.isEmpty()) {
            return false;
        }
        for (SymbolId mention : mentionAliases) {
            String mentionValue = mention.value();
            for (String type : types) {
                if (type.equals(mentionValue)) {
                    return true;
                }
                String normalizedType = support.normalizeTypeToken(type);
                String normalizedMention = support.normalizeTypeToken(mentionValue);
                if (normalizedType.equals(normalizedMention)) {
                    return true;
                }
                if (ontology.isSubclassOf(type, mentionValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatCapabilityLossSentence(SymbolId subject) {
        RelationAssertion assertion = findBooleanAssertion(subject, "control", false);
        if (assertion != null) {
            return answerRenderer.formatAssertionSentence(assertion);
        }
        return normalizeCapabilityLossLine(subject);
    }

    private String formatRecoverySentence(SymbolId agent, SymbolId target) {
        return "Recovery: " + displayValue(agent) + " was active during recovery.";
    }

    private RelationAssertion findBooleanAssertion(SymbolId subject, String predicate, boolean value) {
        if (subject == null) {
            return null;
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!subject.equals(assertion.subject())) {
                continue;
            }
            if (!predicate.equals(support.localName(assertion.predicate()))) {
                continue;
            }
            Boolean objectValue = support.booleanConcept(assertion.object());
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
            String value = target.value().toLowerCase(Locale.ROOT);
            if (value.contains("instability")) {
                return false;
            }
            if (value.contains("stable") || value.contains("stability")) {
                return true;
            }
        }
        if (goal != null && goal.modifier() != null) {
            String modifier = goal.modifier().toLowerCase(Locale.ROOT);
            if (modifier.contains("recovery")) {
                return true;
            }
        }
        return false;
    }

    private boolean wantsRecoveryClause(QueryGoal goal) {
        if (goal != null && goal.modifier() != null) {
            String modifier = goal.modifier().toLowerCase(Locale.ROOT);
            if (modifier.contains("recovery") || modifier.contains("restor") || modifier.contains("regain")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "restor", "recovery", "regain");
    }

    private String summarizeRecoveryExplanation(QueryGoal goal,
                                                String predicate,
                                                ExplanationCandidate candidate,
                                                List<String> fallback) {
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

    private String firstRecoveryHint(List<String> sentences) {
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

    String renderEntityAnswer(String value, QueryGoal goal) {
        return renderEntityAnswer(value, goal, null);
    }

    String renderEntityAnswer(String value, QueryGoal goal, AnswerRole role) {
        if (value == null || value.isBlank()) {
            return "No candidates produced.";
        }
        String display = displayValue(value);
        if (role != null) {
            switch (role) {
                case ROOT_FAILURE_COMPONENT -> {
                    return "The most likely failed component was " + display + ".";
                }
                case EVIDENCE_SIGNAL -> {
                    return "The earlier signal was " + display + ".";
                }
                case RECOVERY_AGENT -> {
                    return "The restoring system was " + display + ".";
                }
            }
        }
        String label = answerLabel(goal);
        if (label != null) {
            String verb = isPlural(label) ? "were" : "was";
            return "The " + label + " " + verb + " " + display + ".";
        }
        return "The answer was " + display + ".";
    }

    String renderRootFailureComponent(String value, QueryGoal goal) {
        return renderEntityAnswer(value, goal, AnswerRole.ROOT_FAILURE_COMPONENT);
    }

    String renderEntityList(java.util.List<String> values, QueryGoal goal) {
        if (values == null || values.isEmpty()) {
            return "No candidates produced.";
        }
        java.util.List<String> rendered = new java.util.ArrayList<>();
        for (String value : values) {
            rendered.add(displayValue(value));
        }
        String list = String.join(", ", rendered);
        boolean pluralList = rendered.size() > 1;
        String label = answerLabel(goal);
        if (label != null) {
            String noun = label;
            if (pluralList && !isPlural(noun)) {
                noun = noun + "s";
            }
            String verb = pluralList ? "were" : "was";
            return "The " + noun + " " + verb + " " + list + ".";
        }
        if (pluralList) {
            return "The answers were " + list + ".";
        }
        return "The answer was " + list + ".";
    }

    private boolean isPlural(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("\\s+");
        String last = tokens[tokens.length - 1];
        return last.endsWith("s") && !last.endsWith("ss");
    }

    private String answerLabel(QueryGoal goal) {
        if (goal == null) {
            return null;
        }
        String candidate = goal.expectedType();
        if (candidate == null || candidate.isBlank()) {
            candidate = goal.entityType();
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String label = displayValue(candidate);
        if (label.isBlank()) {
            return null;
        }
        String normalized = label.toLowerCase(Locale.ROOT).trim();
        if ("entity".equals(normalized) || "concept".equals(normalized)) {
            return null;
        }
        return label;
    }

    private SymbolId selectBestRecoveryAgent(ExplanationCandidate candidate, SymbolId target) {
        List<SymbolId> agents = new ArrayList<>(candidate.recoveryAgents());
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

    private List<SymbolId> findRestoreSubjects(SymbolId target) {
        List<SymbolId> subjects = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
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
        Set<SymbolId> visited = new HashSet<>();
        ArrayDeque<SymbolId> queue = new ArrayDeque<>();
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
                    SymbolId cause = support.selectCauseNode(antecedent);
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

    private Set<SymbolId> relationshipAliasSet(SymbolId node) {
        LinkedHashSet<SymbolId> aliases = new LinkedHashSet<>();
        if (node == null) {
            return aliases;
        }
        aliases.add(node);
        String value = node.value();
        String local = value;
        if (local.startsWith("entity:")) {
            local = local.substring("entity:".length());
        } else if (local.startsWith("concept:")) {
            local = local.substring("concept:".length());
        }
        if (local.startsWith("control_") && local.length() > "control_".length()) {
            String swapped = local.substring("control_".length()) + "_control";
            addRelationshipAlias(aliases, "entity:" + swapped);
            addRelationshipAlias(aliases, "concept:" + swapped);
        } else if (local.endsWith("_control") && local.length() > "_control".length()) {
            String swapped = "control_" + local.substring(0, local.length() - "_control".length());
            addRelationshipAlias(aliases, "entity:" + swapped);
            addRelationshipAlias(aliases, "concept:" + swapped);
        }
        String singular = local.endsWith("s") && local.length() > 1 ? local.substring(0, local.length() - 1) : local;
        String plural = local.endsWith("s") ? local : local + "s";
        addRelationshipAlias(aliases, "entity:" + local);
        addRelationshipAlias(aliases, "concept:" + local);
        if (!singular.equals(local)) {
            addRelationshipAlias(aliases, "entity:" + singular);
            addRelationshipAlias(aliases, "concept:" + singular);
        }
        if (!plural.equals(local)) {
            addRelationshipAlias(aliases, "entity:" + plural);
            addRelationshipAlias(aliases, "concept:" + plural);
        }
        return aliases;
    }

    private void addRelationshipAlias(Set<SymbolId> aliases, String value) {
        SymbolId candidate = new SymbolId(value);
        if (isKnownSymbol(candidate)) {
            aliases.add(candidate);
        }
    }

    private boolean isRelationshipPredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return false;
        }
        return switch (predicate) {
            case "type", "rdf:type", "contain", "contains", "control",
                    "use", "used", "act", "actuates",
                    "on", "in", "at", "with", "under", "inside", "near",
                    "beside", "alongside", "next", "next_to", "next-to",
                    "locatedin" -> true;
            default -> false;
        };
    }

    private int relationshipPredicatePriority(String predicate) {
        if (predicate == null) {
            return 50;
        }
        return switch (predicate) {
            case "contain", "contains" -> 0;
            case "control", "use", "used", "act", "actuates" -> 1;
            case "on", "in", "at", "with", "under", "inside", "near",
                    "beside", "alongside", "next", "next_to", "next-to",
                    "locatedin" -> 2;
            case "type", "rdf:type" -> 3;
            default -> 10;
        };
    }

    private boolean preferRicherChain(QueryGoal goal, List<String> structured, List<String> candidateSentences) {
        if (candidateSentences == null || candidateSentences.isEmpty()) {
            return false;
        }
        if (structured == null || structured.isEmpty()) {
            return true;
        }
        if (!wantsChainExplanation(goal) && !wantsEvidenceAlignedChain(goal)) {
            return false;
        }
        return candidateSentences.size() > structured.size() + 1;
    }

    private boolean wantsControlLoss(QueryGoal goal, SymbolId target) {
        return target != null && hasSemanticRole(target, "control_target");
    }

    private String joinWithOutcome(List<String> sentences, QueryGoal goal, SymbolId target) {
        if (sentences == null || sentences.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            unique.add(sentence);
        }
        List<String> output = new ArrayList<>(unique);
        if (shouldAppendOutcome(goal, target) && !containsOutcome(output)) {
            output.add(normalizeOutcomeLine(target));
        }
        return String.join("\n", output);
    }

    private boolean shouldAppendOutcome(QueryGoal goal, SymbolId target) {
        if (target == null) {
            return false;
        }
        if (wantsRuledOutCauses(goal)) {
            return false;
        }
        if (wantsChainExplanation(goal)) {
            return true;
        }
        return containsCue(support.lastInput(),
                "plausible",
                "most likely",
                "caused",
                "cause",
                "best fits",
                "explanation",
                "sequence");
    }

    private List<String> splitLines(String chain) {
        if (chain == null || chain.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : chain.split("\\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            lines.add(line.trim());
        }
        return lines;
    }

    private boolean containsOutcome(List<String> sentences) {
        for (String sentence : sentences) {
            if (sentence != null && sentence.toLowerCase(Locale.ROOT).contains("outcome:")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeOutcomeLine(SymbolId target) {
        if (target == null) {
            return "Outcome: unknown.";
        }
        String value = target.value();
        String display = displayValue(value);
        if (hasSemanticRole(target, "control_target")) {
            return "Outcome: loss of " + display + ".";
        }
        return "Outcome: " + display + ".";
    }

    private String normalizeCapabilityLossLine(SymbolId subject) {
        if (subject == null) {
            return "Capability loss: unknown.";
        }
        String display = displayValue(subject);
        return "Capability loss: " + display + ".";
    }

    private boolean hasSemanticRole(SymbolId id, String role) {
        if (id == null || role == null || role.isBlank()) {
            return false;
        }
        String cacheKey = id.value() + "|" + role.toLowerCase(Locale.ROOT);
        Boolean cached = semanticRoleCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String iri = resolveEntityIri(id);
        if (iri == null) {
            semanticRoleCache.put(cacheKey, false);
            return false;
        }
        boolean result = annotationResolver.annotationValue(iri, com.sahr.ontology.SahrAnnotationVocabulary.SEMANTIC_ROLE)
                .map(value -> roleMatches(value, role))
                .orElse(false);
        semanticRoleCache.put(cacheKey, result);
        return result;
    }

    private boolean roleMatches(String value, String role) {
        if (value == null || role == null) {
            return false;
        }
        String target = role.trim().toLowerCase(Locale.ROOT);
        for (String part : value.split("[,;|]")) {
            if (part.trim().toLowerCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private String resolveEntityIri(SymbolId id) {
        if (id == null || id.value() == null) {
            return null;
        }
        String cached = entityIriCache.get(id);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String token = annotationResolver.normalizeLabelToToken(displayValue(id));
        if (token.isBlank()) {
            entityIriCache.put(id, "");
            return null;
        }
        for (String iri : annotationResolver.entityIrisByLabel(token)) {
            entityIriCache.put(id, iri);
            return iri;
        }
        entityIriCache.put(id, "");
        return null;
    }

    private void refreshIndexesIfNeeded() {
        long currentVersion = graph.version();
        if (currentVersion == indexVersion) {
            return;
        }
        synchronized (indexLock) {
            if (currentVersion == indexVersion) {
                return;
            }
            labelTokenIndex = new HashMap<>();
            labelPhrasesByEntity = new HashMap<>();
            dependentsByResource = new HashMap<>();
            assertionsByLocalPredicate = new HashMap<>();
            evidencePredicateWeight = new HashMap<>();
            evidenceSignalWeightBySubject = new HashMap<>();
            temporalContextBySymbol = new HashMap<>();
            entityIriCache = new HashMap<>();
            semanticRoleCache = new HashMap<>();
            temporalSupportCache = new HashMap<>();
            evidenceAlignmentCache = new HashMap<>();
            structuredChainCache = new HashMap<>();
            evidenceSignalCache = new HashMap<>();
            dependencyFailureCache = new HashMap<>();
            explanationCandidateCache = new HashMap<>();
            temporalContextCache = new HashMap<>();
            for (EntityNode entity : graph.getAllEntities()) {
                SymbolId id = entity.id();
                if (id == null) {
                    continue;
                }
                List<String> phrases = buildLabelPhrases(id);
                labelPhrasesByEntity.put(id, phrases);
                for (String phrase : phrases) {
                    for (String token : tokenize(phrase)) {
                        labelTokenIndex.computeIfAbsent(token, key -> new HashSet<>()).add(id);
                    }
                }
            }

            for (RelationAssertion assertion : graph.getAllAssertions()) {
                if (assertion == null) {
                    continue;
                }
                String local = support.localName(assertion.predicate());
                assertionsByLocalPredicate
                        .computeIfAbsent(local, key -> new ArrayList<>())
                        .add(assertion);
                if ("poweredby".equals(local) || "require".equals(local) || "requires".equals(local)) {
                    dependentsByResource
                            .computeIfAbsent(assertion.object(), key -> new HashSet<>())
                            .add(assertion.subject());
                }
                if ("before".equals(local) || "after".equals(local) || "during".equals(local)) {
                    if (assertion.subject() != null) {
                        temporalContextBySymbol.put(assertion.subject(), true);
                    }
                    if (assertion.object() != null) {
                        temporalContextBySymbol.put(assertion.object(), true);
                    }
                }
                double weight = predicateEvidenceWeightCached(assertion.predicate());
                if (weight > 0.0 && hasSemanticRole(assertion.subject(), "evidence_signal")) {
                    Double existing = evidenceSignalWeightBySubject.get(assertion.subject());
                    if (existing == null || weight > existing) {
                        evidenceSignalWeightBySubject.put(assertion.subject(), weight);
                    }
                }
            }
            indexVersion = currentVersion;
        }
    }

    private List<String> buildLabelPhrases(SymbolId id) {
        Set<String> phrases = new LinkedHashSet<>();
        String display = displayValue(id);
        if (display != null && !display.isBlank()) {
            phrases.add(normalizePhrase(display));
        }
        String value = id.value();
        if (value != null && !value.isBlank()) {
            phrases.add(normalizePhrase(value));
        }
        return new ArrayList<>(phrases);
    }

    private void removeEntityTokens(SymbolId id) {
        List<String> phrases = labelPhrasesByEntity.remove(id);
        if (phrases == null || phrases.isEmpty()) {
            return;
        }
        for (String phrase : phrases) {
            for (String token : tokenize(phrase)) {
                Set<SymbolId> ids = labelTokenIndex.get(token);
                if (ids == null) {
                    continue;
                }
                ids.remove(id);
                if (ids.isEmpty()) {
                    labelTokenIndex.remove(token);
                }
            }
        }
    }

    private String normalizePhrase(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }

    private String normalizeInput(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT);
    }

    private Set<SymbolId> findEntitiesByTokens(String normalizedInput) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            return Set.of();
        }
        Set<SymbolId> matches = new HashSet<>();
        for (String token : tokenize(normalizedInput)) {
            Set<SymbolId> ids = labelTokenIndex.get(token);
            if (ids != null) {
                matches.addAll(ids);
            }
        }
        return matches;
    }

    private List<RelationAssertion> assertionsByLocalPredicate(String... predicates) {
        List<RelationAssertion> results = new ArrayList<>();
        for (String predicate : predicates) {
            List<RelationAssertion> entries = assertionsByLocalPredicate.get(predicate);
            if (entries != null) {
                results.addAll(entries);
            }
        }
        return results;
    }

    private boolean connectsMentionPair(RelationAssertion assertion, List<Set<SymbolId>> mentionAliases) {
        int subjectIndex = -1;
        int objectIndex = -1;
        for (int i = 0; i < mentionAliases.size(); i++) {
            Set<SymbolId> aliasSet = mentionAliases.get(i);
            if (subjectIndex < 0 && aliasSet.contains(assertion.subject())) {
                subjectIndex = i;
            }
            if (objectIndex < 0 && aliasSet.contains(assertion.object())) {
                objectIndex = i;
            }
            if (subjectIndex >= 0 && objectIndex >= 0) {
                break;
            }
        }
        return subjectIndex >= 0 && objectIndex >= 0 && subjectIndex != objectIndex;
    }

    private String backupForFallback(SymbolId subject, SymbolId object) {
        List<SymbolId> subjectCandidates = aliasBridge.expandAliasSymbols(subject);
        List<SymbolId> objectCandidates = aliasBridge.expandAliasSymbols(object);
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            if (!"backupfor".equals(support.localName(consequent.predicate()))) {
                continue;
            }
            if (!subjectCandidates.isEmpty() && !subjectCandidates.contains(consequent.subject())) {
                continue;
            }
            if (!objectCandidates.isEmpty() && !objectCandidates.contains(consequent.object())) {
                continue;
            }
            return answerRenderer.formatRuleSentence(rule);
        }
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!"backupfor".equals(support.localName(assertion.predicate()))) {
                continue;
            }
            if (!subjectCandidates.isEmpty() && !subjectCandidates.contains(assertion.subject())) {
                continue;
            }
            if (!objectCandidates.isEmpty() && !objectCandidates.contains(assertion.object())) {
                continue;
            }
            return answerRenderer.formatAssertionSentence(assertion);
        }
        if (subject != null) {
            for (RuleAssertion rule : graph.getAllRules()) {
                RelationAssertion consequent = rule.consequent();
                if (!"backupfor".equals(support.localName(consequent.predicate()))) {
                    continue;
                }
                if (subjectCandidates.contains(consequent.subject())) {
                    return answerRenderer.formatRuleSentence(rule);
                }
            }
        }
        return null;
    }
}
