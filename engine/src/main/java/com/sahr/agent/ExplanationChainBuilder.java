package com.sahr.agent;

import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;
import com.sahr.ontology.SahrAnnotationVocabulary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.ArrayList;

final class ExplanationChainBuilder {
    interface Formatter {
        String localName(String predicate);

        String formatAssertionSentence(RelationAssertion assertion);

        String formatRuleSentence(RuleAssertion rule);

        String formatCausalSentence(RelationAssertion assertion, SymbolId cause, SymbolId effect);

        String normalizeTypeToken(String raw);
    }

    private final KnowledgeBase graph;
    @SuppressWarnings("unused")
    private final OntologyService ontology;
    private final Formatter formatter;
    private final ToDoubleFunction<String> specificityScore;
    private final OntologyAnnotationResolver annotationResolver;
    private final Object indexLock = new Object();
    private long indexVersion = -1;
    private java.util.Map<String, java.util.Map<SymbolId, List<RelationAssertion>>> forwardAssertionsByPredicate = new java.util.HashMap<>();
    private java.util.Map<String, java.util.Map<SymbolId, List<RelationAssertion>>> reverseAssertionsByPredicate = new java.util.HashMap<>();
    private java.util.Map<String, Set<String>> predicatesByLocalName = new java.util.HashMap<>();
    private java.util.Map<String, List<RelationAssertion>> assertionsByLocalName = new java.util.HashMap<>();
    private java.util.Map<SymbolId, List<RuleAssertion>> rulesByConsequent = new java.util.HashMap<>();
    private java.util.Map<String, Double> evidenceWeightByPredicate = new java.util.HashMap<>();
    private java.util.Set<String> nonEvidencePredicates = new java.util.HashSet<>();
    private java.util.Map<String, Double> temporalSupportCache = new java.util.HashMap<>();
    private java.util.Map<SymbolId, Double> telemetrySupportCache = new java.util.HashMap<>();
    private List<SymbolId> cachedComponentFailures = List.of();
    private List<SymbolId> cachedSubsystemFailures = List.of();
    private List<SymbolId> cachedCapabilityLosses = List.of();
    private List<SymbolId> cachedRecoveryAgents = List.of();
    private java.util.Map<SymbolId, List<SymbolId>> cachedEvidenceNodesByTarget = new java.util.HashMap<>();
    private java.util.Map<SymbolId, List<SymbolId>> cachedPrecursorSignalsByTarget = new java.util.HashMap<>();
    private boolean failureCacheReady = false;
    private boolean capabilityLossCacheReady = false;
    private boolean recoveryAgentCacheReady = false;

    ExplanationChainBuilder(KnowledgeBase graph,
                            OntologyService ontology,
                            Formatter formatter,
                            ToDoubleFunction<String> specificityScore,
                            OntologyAnnotationResolver annotationResolver) {
        this.graph = graph;
        this.ontology = ontology;
        this.formatter = formatter;
        this.specificityScore = specificityScore;
        this.annotationResolver = annotationResolver;
    }

    List<String> buildExplanationChain(SymbolId target, int maxDepth) {
        List<String> sentences = new ArrayList<>();
        if (target == null) {
            return sentences;
        }
        Set<String> seen = new HashSet<>();
        sentences.addAll(buildExplanationChainFrom(target, maxDepth, seen));
        if (sentences.isEmpty()) {
            sentences.addAll(collectTemporalEvidence(target, null, maxDepth, seen));
        }
        return sentences;
    }

    ExplanationCandidate buildExplanationCandidate(SymbolId target, int maxDepth) {
        List<String> sentences = buildExplanationChain(target, maxDepth);
        List<SymbolId> recoveryAgents = collectRecoveryAgents();
        List<SymbolId> evidenceNodes = collectEvidenceNodes(target);
        List<SymbolId> precursorSignals = collectPrecursorSignals(target);
        List<SymbolId> componentFailures = collectFailures(false);
        List<SymbolId> subsystemFailures = collectFailures(true);
        List<SymbolId> capabilityLosses = collectCapabilityLosses();
        List<SymbolId> outcomes = target == null ? List.of() : List.of(target);
        return new ExplanationCandidate(
                sentences,
                precursorSignals,
                componentFailures,
                subsystemFailures,
                capabilityLosses,
                outcomes,
                recoveryAgents,
                evidenceNodes
        );
    }

    List<String> buildExplanationChainFrom(SymbolId start, int maxDepth, Set<String> seen) {
        List<String> sentences = new ArrayList<>();
        if (start == null) {
            return sentences;
        }
        Set<SymbolId> visited = new HashSet<>();
        SymbolId current = start;
        visited.add(current);
        for (int depth = 0; depth < maxDepth; depth++) {
            RelationAssertion causeAssertion = selectBestCauseAssertion(current);
            RuleAssertion rule = selectBestRuleForConsequent(current);
            double assertionScore = scoreCauseAssertion(causeAssertion, current);
            double ruleScore = scoreRuleConsequent(rule, current);
            if (rule != null && ruleScore >= assertionScore) {
                String sentence = formatter.formatRuleSentence(rule);
                if (seen.add(sentence)) {
                    sentences.add(sentence);
                }
                SymbolId cause = selectCauseNode(rule.antecedent());
                if (cause == null || !visited.add(cause)) {
                    break;
                }
                sentences.addAll(collectTemporalEvidence(cause, current, maxDepth, seen));
                current = cause;
                continue;
            }
            if (causeAssertion != null) {
                SymbolId cause = causeFromAssertion(causeAssertion, current);
                if (cause == null) {
                    break;
                }
                String sentence = formatter.formatCausalSentence(causeAssertion, cause, current);
                if (seen.add(sentence)) {
                    sentences.add(sentence);
                }
                sentences.addAll(collectTemporalEvidence(cause, current, maxDepth, seen));
                if (!visited.add(cause)) {
                    break;
                }
                current = cause;
                continue;
            }
            break;
        }
        return sentences;
    }

    RelationAssertion selectBestCauseAssertion(SymbolId effect) {
        List<RelationAssertion> candidates = new ArrayList<>();
        if (effect == null) {
            return null;
        }
        refreshIndexesIfNeeded();
        candidates.addAll(assertionsByLocalNameAndObject("cause", effect));
        candidates.addAll(assertionsByLocalNameAndSubject("causedby", effect));
        if (candidates.isEmpty()) {
            return null;
        }
        RelationAssertion best = candidates.get(0);
        double bestScore = causeEvidenceScore(causeFromAssertion(best, effect), effect);
        for (RelationAssertion candidate : candidates) {
            SymbolId cause = causeFromAssertion(candidate, effect);
            if (cause == null) {
                continue;
            }
            double score = causeEvidenceScore(cause, effect);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    RuleAssertion selectBestRuleForConsequent(SymbolId effect) {
        if (effect == null) {
            return null;
        }
        refreshIndexesIfNeeded();
        List<RuleAssertion> candidates = rulesByConsequent.get(effect);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        RuleAssertion best = candidates.get(0);
        SymbolId bestCause = selectCauseNode(best.antecedent());
        double bestScore = bestCause == null ? 0.0 : causeEvidenceScore(bestCause, effect);
        for (RuleAssertion candidate : candidates) {
            SymbolId cause = selectCauseNode(candidate.antecedent());
            double score = cause == null ? 0.0 : causeEvidenceScore(cause, effect);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    List<String> buildRecoveryEvidence(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        refreshIndexesIfNeeded();
        List<RelationAssertion> candidates = new ArrayList<>();
        candidates.addAll(assertionsByLocalName("during"));
        candidates.addAll(assertionsByLocalName("after"));
        for (RelationAssertion assertion : candidates) {
            if (!hasSemanticRole(assertion.object(), "recovery_phase")) {
                continue;
            }
            sentences.add(formatter.formatAssertionSentence(assertion));
            if (sentences.size() >= limit) {
                return sentences;
            }
        }
        return sentences;
    }

    List<SymbolId> collectRecoveryAgents() {
        if (recoveryAgentCacheReady) {
            return cachedRecoveryAgents;
        }
        List<SymbolId> agents = new ArrayList<>();
        refreshIndexesIfNeeded();
        List<RelationAssertion> candidates = new ArrayList<>();
        candidates.addAll(assertionsByLocalName("during"));
        candidates.addAll(assertionsByLocalName("after"));
        for (RelationAssertion assertion : candidates) {
            if (!hasSemanticRole(assertion.object(), "recovery_phase")) {
                continue;
            }
            SymbolId subject = assertion.subject();
            if (subject == null || subject.value() == null) {
                continue;
            }
            if (hasSemanticRole(subject, "evidence_signal") || hasSemanticRole(subject, "temporal_marker")) {
                continue;
            }
            agents.add(subject);
        }
        cachedRecoveryAgents = List.copyOf(agents);
        recoveryAgentCacheReady = true;
        return cachedRecoveryAgents;
    }

    List<SymbolId> collectEvidenceNodes(SymbolId target) {
        if (annotationResolver == null || target == null) {
            return List.of();
        }
        List<SymbolId> cached = cachedEvidenceNodesByTarget.get(target);
        if (cached != null) {
            return cached;
        }
        List<SymbolId> evidence = new ArrayList<>();
        refreshIndexesIfNeeded();
        for (String predicate : evidenceWeightByPredicate.keySet()) {
            java.util.Map<SymbolId, List<RelationAssertion>> bySubject = forwardAssertionsByPredicate.get(predicate);
            if (bySubject != null) {
                List<RelationAssertion> matches = bySubject.get(target);
                if (matches != null) {
                    for (RelationAssertion assertion : matches) {
                        evidence.add(assertion.subject());
                    }
                }
            }
            java.util.Map<SymbolId, List<RelationAssertion>> byObject = reverseAssertionsByPredicate.get(predicate);
            if (byObject != null) {
                List<RelationAssertion> matches = byObject.get(target);
                if (matches != null) {
                    for (RelationAssertion assertion : matches) {
                        evidence.add(assertion.subject());
                    }
                }
            }
        }
        List<SymbolId> result = List.copyOf(evidence);
        cachedEvidenceNodesByTarget.put(target, result);
        return result;
    }

    List<SymbolId> collectPrecursorSignals(SymbolId target) {
        if (annotationResolver == null || target == null) {
            return List.of();
        }
        List<SymbolId> cached = cachedPrecursorSignalsByTarget.get(target);
        if (cached != null) {
            return cached;
        }
        List<SymbolId> signals = new ArrayList<>();
        refreshIndexesIfNeeded();
        for (String predicate : evidenceWeightByPredicate.keySet()) {
            java.util.Map<SymbolId, List<RelationAssertion>> byObject = reverseAssertionsByPredicate.get(predicate);
            if (byObject == null) {
                continue;
            }
            List<RelationAssertion> matches = byObject.get(target);
            if (matches == null) {
                continue;
            }
            for (RelationAssertion assertion : matches) {
                signals.add(assertion.subject());
            }
        }
        List<SymbolId> result = List.copyOf(signals);
        cachedPrecursorSignalsByTarget.put(target, result);
        return result;
    }

    List<SymbolId> collectFailures(boolean includeRules) {
        ensureFailureCaches();
        return includeRules ? cachedSubsystemFailures : cachedComponentFailures;
    }

    List<SymbolId> collectCapabilityLosses() {
        if (capabilityLossCacheReady) {
            return cachedCapabilityLosses;
        }
        List<SymbolId> losses = new ArrayList<>();
        refreshIndexesIfNeeded();
        for (RelationAssertion assertion : assertionsByLocalName("control")) {
            if (isBooleanFalse(assertion.object())) {
                losses.add(assertion.subject());
            }
        }
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            String predicate = formatter.localName(consequent.predicate());
            if (!"control".equals(predicate)) {
                continue;
            }
            if (isBooleanFalse(consequent.object())) {
                losses.add(consequent.subject());
            }
        }
        cachedCapabilityLosses = List.copyOf(losses);
        capabilityLossCacheReady = true;
        return cachedCapabilityLosses;
    }

    private boolean isBooleanTrue(SymbolId id) {
        if (id == null || id.value() == null) {
            return false;
        }
        String value = id.value();
        if (value.startsWith("concept:")) {
            value = value.substring("concept:".length());
        }
        return "true".equalsIgnoreCase(value);
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

    private boolean failureSelfReference(RelationAssertion assertion) {
        if (assertion == null) {
            return false;
        }
        SymbolId subject = assertion.subject();
        SymbolId object = assertion.object();
        return subject != null && subject.equals(object);
    }

    private boolean isFailureLike(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return false;
        }
        return switch (predicate) {
            case "operate", "function", "work", "respond", "stop", "stop_responding" -> true;
            default -> false;
        };
    }

    private double causeEvidenceScore(SymbolId cause, SymbolId effect) {
        if (cause == null) {
            return 0.0;
        }
        double score = specificityScore.applyAsDouble(cause.value());
        score += temporalSupportScore(cause, effect);
        score += telemetrySupportScore(cause);
        return score;
    }

    private double scoreCauseAssertion(RelationAssertion assertion, SymbolId effect) {
        if (assertion == null) {
            return 0.0;
        }
        SymbolId cause = causeFromAssertion(assertion, effect);
        if (cause == null) {
            return 0.0;
        }
        return causeEvidenceScore(cause, effect) + predicateDynamicWeight(assertion.predicate());
    }

    private double scoreRuleConsequent(RuleAssertion rule, SymbolId effect) {
        if (rule == null) {
            return 0.0;
        }
        SymbolId cause = selectCauseNode(rule.antecedent());
        if (cause == null) {
            return 0.0;
        }
        return causeEvidenceScore(cause, effect) + predicateDynamicWeight(rule.consequent().predicate());
    }

    private double predicateDynamicWeight(String predicate) {
        if (annotationResolver == null || predicate == null || predicate.isBlank()) {
            return 0.0;
        }
        return annotationResolver.resolveObjectPropertyIri(predicate)
                .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.DYNAMIC_WEIGHT))
                .orElse(0.0);
    }

    private double temporalSupportScore(SymbolId cause, SymbolId effect) {
        if (cause == null || effect == null) {
            return 0.0;
        }
        String key = cause.value() + "->" + effect.value();
        Double cached = temporalSupportCache.get(key);
        if (cached != null) {
            return cached;
        }
        double score = 0.0;
        refreshIndexesIfNeeded();
        List<RelationAssertion> candidates = new ArrayList<>();
        candidates.addAll(assertionsByLocalNameAndSubject("before", cause));
        candidates.addAll(assertionsByLocalNameAndSubject("after", cause));
        candidates.addAll(assertionsByLocalNameAndSubject("during", cause));
        candidates.addAll(assertionsByLocalNameAndSubject("before", effect));
        candidates.addAll(assertionsByLocalNameAndSubject("after", effect));
        candidates.addAll(assertionsByLocalNameAndSubject("during", effect));
        for (RelationAssertion assertion : candidates) {
            String predicate = formatter.localName(assertion.predicate());
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
        temporalSupportCache.put(key, score);
        return score;
    }

    private double telemetrySupportScore(SymbolId cause) {
        if (cause == null) {
            return 0.0;
        }
        Double cached = telemetrySupportCache.get(cause);
        if (cached != null) {
            return cached;
        }
        double score = 0.0;
        refreshIndexesIfNeeded();
        for (var entry : evidenceWeightByPredicate.entrySet()) {
            String predicate = entry.getKey();
            double weight = entry.getValue();
            java.util.Map<SymbolId, List<RelationAssertion>> bySubject = forwardAssertionsByPredicate.get(predicate);
            if (bySubject != null) {
                List<RelationAssertion> matches = bySubject.get(cause);
                if (matches != null) {
                    score += weight * matches.size();
                }
            }
            java.util.Map<SymbolId, List<RelationAssertion>> byObject = reverseAssertionsByPredicate.get(predicate);
            if (byObject != null) {
                List<RelationAssertion> matches = byObject.get(cause);
                if (matches != null) {
                    score += weight * matches.size();
                }
            }
        }
        telemetrySupportCache.put(cause, score);
        return score;
    }

    private boolean hasSemanticRole(SymbolId id, String role) {
        if (annotationResolver == null || id == null || role == null || role.isBlank()) {
            return false;
        }
        String iri = resolveEntityIri(id);
        if (iri == null) {
            return false;
        }
        return annotationResolver.annotationValue(iri, SahrAnnotationVocabulary.SEMANTIC_ROLE)
                .map(value -> roleMatches(value, role))
                .orElse(false);
    }

    private boolean roleMatches(String value, String role) {
        if (value == null || role == null) {
            return false;
        }
        String target = role.trim().toLowerCase(java.util.Locale.ROOT);
        for (String part : value.split("[,;|]")) {
            if (part.trim().toLowerCase(java.util.Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private String resolveEntityIri(SymbolId id) {
        if (id == null || id.value() == null) {
            return null;
        }
        String raw = id.value();
        if (raw.startsWith("entity:")) {
            raw = raw.substring("entity:".length());
        } else if (raw.startsWith("concept:")) {
            raw = raw.substring("concept:".length());
        }
        String token = annotationResolver.normalizeLabelToToken(raw);
        if (token.isBlank()) {
            return null;
        }
        for (String iri : annotationResolver.entityIrisByLabel(token)) {
            return iri;
        }
        return null;
    }

    private List<String> collectTemporalEvidence(SymbolId cause,
                                                 SymbolId effect,
                                                 int limit,
                                                 Set<String> seen) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        refreshIndexesIfNeeded();
        List<RelationAssertion> candidates = new ArrayList<>();
        if (cause != null) {
            candidates.addAll(assertionsByLocalNameAndSubject("before", cause));
            candidates.addAll(assertionsByLocalNameAndSubject("after", cause));
            candidates.addAll(assertionsByLocalNameAndSubject("during", cause));
        }
        if (effect != null) {
            candidates.addAll(assertionsByLocalNameAndSubject("before", effect));
            candidates.addAll(assertionsByLocalNameAndSubject("after", effect));
            candidates.addAll(assertionsByLocalNameAndSubject("during", effect));
        }
        if (cause == null && effect == null) {
            return sentences;
        }
        for (RelationAssertion assertion : candidates) {
            boolean matches = false;
            if (cause != null && effect != null) {
                matches = assertion.subject().equals(cause) && assertion.object().equals(effect);
                matches = matches || assertion.subject().equals(effect) && assertion.object().equals(cause);
            } else if (cause != null) {
                matches = assertion.subject().equals(cause);
            } else if (effect != null) {
                matches = assertion.subject().equals(effect);
            }
            if (!matches) {
                continue;
            }
            String sentence = formatter.formatAssertionSentence(assertion);
            if (seen.add(sentence)) {
                sentences.add(sentence);
            }
            if (sentences.size() >= limit) {
                return sentences;
            }
        }
        return sentences;
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
            forwardAssertionsByPredicate = new java.util.HashMap<>();
            reverseAssertionsByPredicate = new java.util.HashMap<>();
            predicatesByLocalName = new java.util.HashMap<>();
            assertionsByLocalName = new java.util.HashMap<>();
            rulesByConsequent = new java.util.HashMap<>();
            evidenceWeightByPredicate = new java.util.HashMap<>();
            nonEvidencePredicates = new java.util.HashSet<>();
            temporalSupportCache = new java.util.HashMap<>();
            telemetrySupportCache = new java.util.HashMap<>();
            cachedComponentFailures = List.of();
            cachedSubsystemFailures = List.of();
            cachedCapabilityLosses = List.of();
            cachedRecoveryAgents = List.of();
            cachedEvidenceNodesByTarget = new java.util.HashMap<>();
            cachedPrecursorSignalsByTarget = new java.util.HashMap<>();
            failureCacheReady = false;
            capabilityLossCacheReady = false;
            recoveryAgentCacheReady = false;
            for (RelationAssertion assertion : graph.getAllAssertions()) {
                if (assertion == null) {
                    continue;
                }
                String predicate = assertion.predicate();
                forwardAssertionsByPredicate
                        .computeIfAbsent(predicate, key -> new java.util.HashMap<>())
                        .computeIfAbsent(assertion.subject(), key -> new java.util.ArrayList<>())
                        .add(assertion);
                reverseAssertionsByPredicate
                        .computeIfAbsent(predicate, key -> new java.util.HashMap<>())
                        .computeIfAbsent(assertion.object(), key -> new java.util.ArrayList<>())
                        .add(assertion);
                String local = formatter.localName(predicate);
                predicatesByLocalName
                        .computeIfAbsent(local, key -> new java.util.HashSet<>())
                        .add(predicate);
                assertionsByLocalName
                        .computeIfAbsent(local, key -> new java.util.ArrayList<>())
                        .add(assertion);
                if (annotationResolver != null && !evidenceWeightByPredicate.containsKey(predicate)
                        && !nonEvidencePredicates.contains(predicate)) {
                    Double weight = annotationResolver.resolveObjectPropertyIri(predicate)
                            .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                            .orElse(null);
                    if (weight != null && weight > 0.0) {
                        evidenceWeightByPredicate.put(predicate, weight);
                    } else {
                        nonEvidencePredicates.add(predicate);
                    }
                }
            }
            for (RuleAssertion rule : graph.getAllRules()) {
                if (rule == null) {
                    continue;
                }
                RelationAssertion consequent = rule.consequent();
                if (consequent == null) {
                    continue;
                }
                SymbolId subject = consequent.subject();
                if (subject != null) {
                    rulesByConsequent
                            .computeIfAbsent(subject, key -> new java.util.ArrayList<>())
                            .add(rule);
                }
                SymbolId object = consequent.object();
                if (object != null) {
                    rulesByConsequent
                            .computeIfAbsent(object, key -> new java.util.ArrayList<>())
                            .add(rule);
                }
            }
            indexVersion = currentVersion;
        }
    }

    private void ensureFailureCaches() {
        if (failureCacheReady) {
            return;
        }
        List<SymbolId> componentFailures = new ArrayList<>();
        refreshIndexesIfNeeded();
        for (RelationAssertion assertion : assertionsByLocalName("fail")) {
            if (isBooleanTrue(assertion.object()) || failureSelfReference(assertion)) {
                componentFailures.add(assertion.subject());
            }
        }
        for (String predicate : List.of("operate", "function", "work", "respond", "stop", "stop_responding")) {
            for (RelationAssertion assertion : assertionsByLocalName(predicate)) {
                if (isBooleanFalse(assertion.object())) {
                    componentFailures.add(assertion.subject());
                }
            }
        }
        List<SymbolId> subsystemFailures = new ArrayList<>(componentFailures);
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            RelationAssertion antecedent = rule.antecedent();
            String predicate = formatter.localName(consequent.predicate());
            if ("fail".equals(predicate)) {
                if (isBooleanTrue(consequent.object()) || failureSelfReference(consequent)) {
                    subsystemFailures.add(consequent.subject());
                }
                if ("fail".equals(formatter.localName(antecedent.predicate()))
                        && (isBooleanTrue(antecedent.object()) || failureSelfReference(antecedent))) {
                    subsystemFailures.add(antecedent.subject());
                }
                continue;
            }
            if (isFailureLike(predicate) && isBooleanFalse(consequent.object())) {
                subsystemFailures.add(consequent.subject());
            }
        }
        cachedComponentFailures = List.copyOf(componentFailures);
        cachedSubsystemFailures = List.copyOf(subsystemFailures);
        failureCacheReady = true;
    }

    private List<RelationAssertion> assertionsByLocalName(String localName) {
        if (localName == null || localName.isBlank()) {
            return List.of();
        }
        List<RelationAssertion> results = assertionsByLocalName.get(localName);
        if (results == null) {
            return List.of();
        }
        return results;
    }

    private List<RelationAssertion> assertionsByLocalNameAndSubject(String localName, SymbolId subject) {
        if (localName == null || subject == null) {
            return List.of();
        }
        List<RelationAssertion> results = new ArrayList<>();
        Set<String> predicates = predicatesByLocalName.get(localName);
        if (predicates == null || predicates.isEmpty()) {
            return results;
        }
        for (String predicate : predicates) {
            java.util.Map<SymbolId, List<RelationAssertion>> bySubject = forwardAssertionsByPredicate.get(predicate);
            if (bySubject == null) {
                continue;
            }
            List<RelationAssertion> matches = bySubject.get(subject);
            if (matches != null && !matches.isEmpty()) {
                results.addAll(matches);
            }
        }
        return results;
    }

    private List<RelationAssertion> assertionsByLocalNameAndObject(String localName, SymbolId object) {
        if (localName == null || object == null) {
            return List.of();
        }
        List<RelationAssertion> results = new ArrayList<>();
        Set<String> predicates = predicatesByLocalName.get(localName);
        if (predicates == null || predicates.isEmpty()) {
            return results;
        }
        for (String predicate : predicates) {
            java.util.Map<SymbolId, List<RelationAssertion>> byObject = reverseAssertionsByPredicate.get(predicate);
            if (byObject == null) {
                continue;
            }
            List<RelationAssertion> matches = byObject.get(object);
            if (matches != null && !matches.isEmpty()) {
                results.addAll(matches);
            }
        }
        return results;
    }

    private SymbolId causeFromAssertion(RelationAssertion assertion, SymbolId effect) {
        if (assertion == null || effect == null) {
            return null;
        }
        String predicate = formatter.localName(assertion.predicate());
        if ("causedby".equals(predicate)) {
            return assertion.object();
        }
        return assertion.subject();
    }

    private SymbolId selectCauseNode(RelationAssertion assertion) {
        if (assertion == null) {
            return null;
        }
        SymbolId subject = assertion.subject();
        SymbolId object = assertion.object();
        if (subject != null && !"concept:true".equals(subject.value())) {
            return subject;
        }
        return object;
    }
}
