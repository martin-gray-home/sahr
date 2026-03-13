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
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"cause".equals(predicate) && !"causedby".equals(predicate)) {
                continue;
            }
            if ("causedby".equals(predicate)) {
                if (assertion.subject().equals(effect)) {
                    candidates.add(assertion);
                }
                continue;
            }
            if (assertion.object().equals(effect)) {
                candidates.add(assertion);
            }
        }
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
        List<RuleAssertion> candidates = new ArrayList<>();
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion consequent = rule.consequent();
            if (consequent.subject().equals(effect) || consequent.object().equals(effect)) {
                candidates.add(rule);
            }
        }
        if (candidates.isEmpty()) {
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
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"during".equals(predicate) && !"after".equals(predicate)) {
                continue;
            }
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
        List<SymbolId> agents = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"during".equals(predicate) && !"after".equals(predicate)) {
                continue;
            }
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
        return agents;
    }

    List<SymbolId> collectEvidenceNodes(SymbolId target) {
        if (annotationResolver == null || target == null) {
            return List.of();
        }
        List<SymbolId> evidence = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            double weight = annotationResolver.resolveObjectPropertyIri(assertion.predicate())
                    .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                    .orElse(0.0);
            if (weight <= 0.0) {
                continue;
            }
            if (assertion.object().equals(target) || assertion.subject().equals(target)) {
                evidence.add(assertion.subject());
            }
        }
        return evidence;
    }

    List<SymbolId> collectPrecursorSignals(SymbolId target) {
        if (annotationResolver == null || target == null) {
            return List.of();
        }
        List<SymbolId> signals = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            double weight = annotationResolver.resolveObjectPropertyIri(assertion.predicate())
                    .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                    .orElse(0.0);
            if (weight <= 0.0) {
                continue;
            }
            if (assertion.object().equals(target)) {
                signals.add(assertion.subject());
            }
        }
        return signals;
    }

    List<SymbolId> collectFailures(boolean includeRules) {
        List<SymbolId> failures = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if ("fail".equals(predicate)) {
                if (isBooleanTrue(assertion.object()) || failureSelfReference(assertion)) {
                    failures.add(assertion.subject());
                }
                continue;
            }
            if (isFailureLike(predicate) && isBooleanFalse(assertion.object())) {
                failures.add(assertion.subject());
            }
        }
        if (includeRules) {
            for (RuleAssertion rule : graph.getAllRules()) {
                RelationAssertion consequent = rule.consequent();
                RelationAssertion antecedent = rule.antecedent();
                String predicate = formatter.localName(consequent.predicate());
                if ("fail".equals(predicate)) {
                    if (isBooleanTrue(consequent.object()) || failureSelfReference(consequent)) {
                        failures.add(consequent.subject());
                    }
                    if ("fail".equals(formatter.localName(antecedent.predicate()))
                            && (isBooleanTrue(antecedent.object()) || failureSelfReference(antecedent))) {
                        failures.add(antecedent.subject());
                    }
                    continue;
                }
                if (isFailureLike(predicate) && isBooleanFalse(consequent.object())) {
                    failures.add(consequent.subject());
                }
            }
        }
        return failures;
    }

    List<SymbolId> collectCapabilityLosses() {
        List<SymbolId> losses = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"control".equals(predicate)) {
                continue;
            }
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
        return losses;
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
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
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

    private double telemetrySupportScore(SymbolId cause) {
        if (cause == null) {
            return 0.0;
        }
        double score = 0.0;
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (annotationResolver == null) {
                continue;
            }
            Double weight = annotationResolver.resolveObjectPropertyIri(assertion.predicate())
                    .flatMap(iri -> annotationResolver.annotationDouble(iri, SahrAnnotationVocabulary.EVIDENCE_WEIGHT))
                    .orElse(null);
            if (weight == null || weight == 0.0) {
                continue;
            }
            if (assertion.object().equals(cause) || assertion.subject().equals(cause)) {
                score += weight;
            }
        }
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
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            String predicate = formatter.localName(assertion.predicate());
            if (!"before".equals(predicate) && !"after".equals(predicate) && !"during".equals(predicate)) {
                continue;
            }
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
