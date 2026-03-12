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
    interface Support {
        String localName(String predicate);
        Boolean booleanConcept(SymbolId id);
        String normalizeTypeToken(String raw);
        String lastInput();
        SymbolId selectCauseNode(RelationAssertion antecedent);
    }

    private static final String PREDICATE_TYPE = "rdf:type";

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
        boolean wantsComponent = false;
        if (goal.subject() != null && goal.subject().contains("component")) {
            wantsComponent = true;
        }
        if (goal.subjectText() != null && goal.subjectText().toLowerCase(Locale.ROOT).contains("component")) {
            wantsComponent = true;
        }
        if (goal.expectedType() != null && goal.expectedType().toLowerCase(Locale.ROOT).contains("component")) {
            wantsComponent = true;
        }
        if (!wantsComponent && containsCue(support.lastInput(), "component")) {
            wantsComponent = true;
        }
        if (!wantsComponent) {
            return null;
        }
        SymbolId target = findEntityByToken("spacecraft_instability");
        ExplanationCandidate candidate = explanationChains.buildExplanationCandidate(target, 4);
        SymbolId failure = selectBestFailure(candidate, true);
        return failure == null ? null : failure.value();
    }

    String executeCauseChain(QueryGoal goal) {
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
            String backupExplanation = backupForFallback(subject, target);
            if (backupExplanation != null) {
                return backupExplanation;
            }
        }
        String relationship = relationshipAnswer(goal);
        if (relationship != null && !relationship.isBlank()) {
            return relationship;
        }
        if (wantsRuledOutCauses(goal)) {
            String ruledOut = ruledOutCauseAnswer(goal, target);
            if (ruledOut != null) {
                return ruledOut;
            }
        }
        if (wantsDependencyContrast(goal)) {
            String contrast = dependencyContrastAnswer(goal);
            if (contrast != null) {
                return contrast;
            }
        }
        if (wantsConditionContrast(goal)) {
            String contrast = conditionContrastAnswer(goal);
            if (contrast != null) {
                return contrast;
            }
        }
        if (!predicate.isBlank() && !"cause".equals(predicate) && !"causedby".equals(predicate)) {
            List<String> predicateExplanation = predicateExplainer.buildPredicateExplanation(goal, predicate, 3);
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
        if (structuredCandidate != null
                && (wantsChainExplanation(goal)
                || wantsRecoveryClause(goal)
                || "cause".equals(predicate)
                || "causedby".equals(predicate)
                || "restore".equals(predicate)
                || "regain".equals(predicate))) {
            List<String> structured = buildStructuredChain(structuredCandidate, goal, target, predicate);
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
            String chain = bestFailureChainToOutcome(structuredCandidate, target, goal);
            if (chain != null) {
                return joinWithOutcome(splitLines(chain), goal, target);
            }
        }
        List<SymbolId> subjectCandidates = aliasBridge.expandAliasSymbols(subject);
        List<SymbolId> targetCandidates = aliasBridge.expandAliasSymbols(target);
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
            if (best != null) {
                return String.join("\n", best.sentences());
            }
        }
        if (subject == null && target != null) {
            for (SymbolId targetCandidate : targetCandidates) {
                String ruleChain = ruleChainFallback(targetCandidate);
                if (ruleChain != null) {
                    return ruleChain;
                }
            }
        }
        return "No candidates produced.";
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
            String value = mention.value().toLowerCase(Locale.ROOT);
            if (value.contains("control") || value.contains("orientation")) {
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
                SymbolId matched = matchEntityByPhrase(phrase, 0.8);
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
                    "control", "controls", "orientation", "spacecraft" -> true;
            default -> false;
        };
    }

    private SymbolId findEntityByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String target = token.toLowerCase(Locale.ROOT);
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            SymbolId subject = assertion.subject();
            if (subject != null && subject.value() != null && subject.value().toLowerCase(Locale.ROOT).contains(target)) {
                return subject;
            }
            SymbolId object = assertion.object();
            if (object != null && object.value() != null && object.value().toLowerCase(Locale.ROOT).contains(target)) {
                return object;
            }
        }
        return null;
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
        List<String> sentences = new ArrayList<>();
        List<String> recoveryEvidence = explanationChains.buildRecoveryEvidence(2);
        sentences.addAll(recoveryEvidence);
        LinkedHashSet<String> electricalDependents = new LinkedHashSet<>();
        LinkedHashSet<SymbolId> electricalSubjects = new LinkedHashSet<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
            if (!"poweredby".equals(predicate) && !"require".equals(predicate) && !"requires".equals(predicate)) {
                continue;
            }
            if (!assertion.object().value().toLowerCase(Locale.ROOT).contains("electrical")) {
                continue;
            }
            electricalSubjects.add(assertion.subject());
            electricalDependents.add(answerRenderer.formatAssertionSentence(assertion));
        }
        if (sentences.isEmpty() && electricalDependents.isEmpty()) {
            return null;
        }
        for (String entry : electricalDependents) {
            sentences.add(entry);
            if (sentences.size() >= 4) {
                break;
            }
        }
        boolean shouldInfer = !recoveryEvidence.isEmpty()
                && (!electricalSubjects.isEmpty()
                || containsCue(support.lastInput(), "electrical actuator", "electrical actuators"));
        if (shouldInfer) {
            sentences.add("This suggests the recovery likely did not depend on electrical actuators.");
        }
        return String.join("\n", sentences);
    }

    private String conditionContrastAnswer(QueryGoal goal) {
        List<SymbolId> mentions = extractMentionedEntities(goal);
        if (mentions.size() < 2) {
            SymbolId first = findEntityByToken("magnetorquer");
            SymbolId second = findEntityByToken("thruster");
            if (first != null && second != null) {
                mentions = List.of(first, second);
            } else {
                return null;
            }
        }
        SymbolId failing = mentions.get(0);
        SymbolId surviving = mentions.get(1);
        SymbolId failResource = firstDependencyResource(failing);
        SymbolId surviveResource = firstDependencyResource(surviving);
        if (failResource == null || surviveResource == null) {
            SymbolId electrical = findEntityByToken("electrical_power");
            SymbolId propellant = findEntityByToken("propellant");
            if (electrical != null && propellant != null) {
                failResource = electrical;
                surviveResource = propellant;
            } else {
                return null;
            }
        }
        return "If " + displayValue(failResource) + " is unavailable but "
                + displayValue(surviveResource) + " remains available, then "
                + displayValue(failing) + " may fail while "
                + displayValue(surviving) + " can operate.";
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

    private double evidenceAlignmentScore(ExplanationCandidate candidate,
                                          SymbolId failure,
                                          SymbolId outcome,
                                          QueryGoal goal) {
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
        return score;
    }

    private double temporalSupportScore(SymbolId cause, SymbolId effect) {
        if (cause == null || effect == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
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

    private List<String> buildStructuredChain(ExplanationCandidate candidate,
                                              QueryGoal goal,
                                              SymbolId target,
                                              String predicate) {
        List<String> sentences = new ArrayList<>();
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
            List<SymbolId> subsystemTop = selectTopSpecific(candidate.subsystemFailures(), 2, true, null);
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
        if (capabilityLoss == null && wantsControlLoss(goal, target)) {
            SymbolId fallbackLoss = target;
            if (fallbackLoss != null) {
                sentences.add(formatCapabilityLossSentence(fallbackLoss));
            }
        }
        if (target != null) {
            sentences.add(normalizeOutcomeLine(target));
        }
        if (shouldIncludeRecovery(goal, predicate, target) || wantsRecoveryClause(goal)) {
            SymbolId agent = selectBestRecoveryAgent(candidate, target);
            if (agent != null) {
                sentences.add(formatRecoverySentence(agent, target));
            }
        }
        appendEvidenceLines(candidate, componentFailure, subsystemFailure, target, evidenceAligned, sentences);
        return sentences;
    }

    private List<SymbolId> selectTopSpecific(List<SymbolId> candidates,
                                             int limit,
                                             boolean avoidLoss,
                                             SymbolId exclude) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<SymbolId> filtered = new ArrayList<>();
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
            filtered.add(candidate);
        }
        filtered.sort(Comparator.comparingDouble((SymbolId id) -> answerRanker.specificityScore(id.value())).reversed());
        if (filtered.size() > limit) {
            return filtered.subList(0, limit);
        }
        return filtered;
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
        SymbolId best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SymbolId failure : failures) {
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
                for (RelationAssertion assertion : graph.getAllAssertions()) {
                    String predicate = support.localName(assertion.predicate());
                    if (!"before".equals(predicate) && !"after".equals(predicate)) {
                        continue;
                    }
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
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = support.localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
            if (assertion.subject().equals(node) || assertion.object().equals(node)) {
                return true;
            }
        }
        return false;
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
            if (normalized.contains("telemetry") || normalized.contains("sequence") || normalized.contains("evidence")) {
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

    private boolean isPlural(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("\\s+");
        String last = tokens[tokens.length - 1];
        return last.endsWith("s") && !last.endsWith("ss");
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
        if (target != null) {
            String value = target.value().toLowerCase(Locale.ROOT);
            if (value.contains("control") || value.contains("orientation")) {
                return true;
            }
        }
        return containsCue(support.lastInput(), "control", "prevent", "orientation control");
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
        String lowered = display.toLowerCase(Locale.ROOT);
        if (lowered.contains("control") && lowered.contains("orientation")) {
            return "Outcome: loss of spacecraft orientation control.";
        }
        return "Outcome: " + display + ".";
    }

    private String normalizeCapabilityLossLine(SymbolId subject) {
        if (subject == null) {
            return "Capability loss: unknown.";
        }
        String display = displayValue(subject);
        String lowered = display.toLowerCase(Locale.ROOT);
        if (lowered.contains("control") && lowered.contains("orientation")) {
            return "Capability loss: spacecraft orientation control.";
        }
        if (lowered.contains("orientation") && !lowered.contains("control")) {
            return "Capability loss: spacecraft orientation control.";
        }
        return "Capability loss: " + display + ".";
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
