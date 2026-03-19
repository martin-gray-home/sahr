package com.sahr.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RuleDerivationService {
    private static final SymbolId GROUND_RULE_BINDING = new SymbolId("__ground_rule__");

    public List<RuleDerivation> derive(KnowledgeBase graph) {
        if (graph == null) {
            return List.of();
        }
        return deriveRuleFrames(graph);
    }

    public List<RuleDerivation> derive(KnowledgeBase graph, KnowledgeBase focusedGraph) {
        if (graph == null) {
            return List.of();
        }
        if (!(focusedGraph instanceof FocusedKnowledgeBase focusedView) || !focusedView.isReduced()) {
            return derive(graph);
        }
        List<RuleDerivation> prioritized = deriveRuleFrames(focusedGraph);
        List<RuleDerivation> complete = deriveRuleFrames(graph);
        if (prioritized.isEmpty()) {
            return complete;
        }
        List<RuleDerivation> merged = new ArrayList<>(prioritized);
        Set<String> seen = new LinkedHashSet<>();
        for (RuleDerivation derivation : prioritized) {
            seen.add(derivationKey(derivation));
        }
        for (RuleDerivation derivation : complete) {
            if (seen.add(derivationKey(derivation))) {
                merged.add(derivation);
            }
        }
        return merged;
    }

    private List<RuleDerivation> deriveRuleFrames(KnowledgeBase graph) {
        if (graph.getAllRuleFrames().isEmpty() || graph.getAllAssertions().isEmpty()) {
            return List.of();
        }
        List<RuleDerivation> derivations = new ArrayList<>();
        for (RuleFrame rule : graph.getAllRuleFrames()) {
            List<SymbolId> bindings = findBindings(rule, graph);
            for (SymbolId binding : bindings) {
                RelationAssertion consequent = instantiateConsequent(rule.consequent(), binding, rule.confidence());
                if (consequent == null || alreadyPresent(consequent, graph)) {
                    continue;
                }
                double evidenceConfidence = averageAntecedentConfidence(rule, binding, graph);
                List<String> evidence = new ArrayList<>();
                String bindingText = "binding " + rule.variable() + "=" + binding;
                evidence.add(bindingText);
                evidence.addAll(matchedAntecedentEvidence(rule, binding, graph));
                String ruleText = rule.toString();
                evidence.add(ruleText);
                List<String> supportingAssertionIds = matchedAntecedentAssertionIds(rule, binding, graph);
                derivations.add(new RuleDerivation(
                        new RelationAssertion(consequent.subject(), consequent.predicate(), consequent.object(),
                                Math.min(1.0, (rule.confidence() + evidenceConfidence) / 2.0)),
                        evidence,
                        supportingAssertionIds,
                        ruleText,
                        bindingText,
                        rule.confidence(),
                        evidenceConfidence,
                        2
                ));
            }
        }
        return derivations;
    }

    private String derivationKey(RuleDerivation derivation) {
        RelationAssertion assertion = derivation.assertion();
        return assertion.subject().value() + "|" + assertion.predicate() + "|" + assertion.object().value();
    }

    private boolean alreadyPresent(RelationAssertion assertion, KnowledgeBase graph) {
        return matchingAssertions(assertion.predicate(), graph).stream()
                .anyMatch(existing -> existing.subject().equals(assertion.subject())
                        && existing.object().equals(assertion.object()));
    }

    private List<SymbolId> findBindings(RuleFrame rule, KnowledgeBase graph) {
        List<RuleAtom> antecedents = rule.antecedents();
        if (antecedents.isEmpty()) {
            return List.of();
        }
        if (!RuleFrames.usesVariable(rule)) {
            return allAntecedentsMatch(antecedents, graph, GROUND_RULE_BINDING)
                    ? List.of(GROUND_RULE_BINDING)
                    : List.of();
        }
        Set<SymbolId> bindings = new LinkedHashSet<>();
        RuleAtom seed = antecedents.get(0);
        if ("rdf:type".equals(seed.predicate())) {
            for (EntityNode entity : graph.getAllEntities()) {
                if (!matchesType(seed, entity)) {
                    continue;
                }
                SymbolId binding = entity.id();
                if (allAntecedentsMatch(antecedents, graph, binding)) {
                    bindings.add(binding);
                }
            }
            return List.copyOf(bindings);
        }
        for (RelationAssertion assertion : matchingAssertions(seed.predicate(), graph)) {
            SymbolId binding = matchBinding(seed, assertion, null);
            if (binding == null) {
                continue;
            }
            if (allAntecedentsMatch(antecedents, graph, binding)) {
                bindings.add(binding);
            }
        }
        return List.copyOf(bindings);
    }

    private boolean allAntecedentsMatch(List<RuleAtom> antecedents,
                                        KnowledgeBase graph,
                                        SymbolId binding) {
        for (RuleAtom atom : antecedents) {
            boolean matched;
            if ("rdf:type".equals(atom.predicate())) {
                matched = graph.findEntity(binding)
                        .map(entity -> matchesType(atom, entity))
                        .orElse(false);
            } else {
                matched = matchingAssertions(atom.predicate(), graph).stream()
                        .anyMatch(assertion -> matchBinding(atom, assertion, binding) != null);
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private SymbolId matchBinding(RuleAtom atom,
                                  RelationAssertion assertion,
                                  SymbolId binding) {
        if (!predicateEquals(atom.predicate(), assertion.predicate())) {
            return null;
        }
        SymbolId nextBinding = matchTerm(atom.subject(), assertion.subject(), binding);
        if (nextBinding == null) {
            return null;
        }
        return matchTerm(atom.object(), assertion.object(), nextBinding);
    }

    private SymbolId matchTerm(RuleTerm term, SymbolId value, SymbolId binding) {
        if (term.isVariable()) {
            if (binding == null) {
                return value;
            }
            return binding.equals(value) ? binding : null;
        }
        if (value == null) {
            return null;
        }
        return term.value().equals(value.value()) ? binding : null;
    }

    private RelationAssertion instantiateConsequent(RuleAtom consequent, SymbolId binding, double confidence) {
        SymbolId subject = resolveTerm(consequent.subject(), binding);
        SymbolId object = resolveTerm(consequent.object(), binding);
        if (subject == null || object == null) {
            return null;
        }
        return new RelationAssertion(subject, consequent.predicate(), object, confidence);
    }

    private SymbolId resolveTerm(RuleTerm term, SymbolId binding) {
        if (term.isVariable()) {
            return binding;
        }
        if (term.value() == null || term.value().isBlank()) {
            return null;
        }
        return new SymbolId(term.value());
    }

    private double averageAntecedentConfidence(RuleFrame rule,
                                               SymbolId binding,
                                               KnowledgeBase graph) {
        double total = 0.0;
        int count = 0;
        for (RuleAtom atom : rule.antecedents()) {
            if ("rdf:type".equals(atom.predicate())) {
                if (graph.findEntity(binding).map(entity -> matchesType(atom, entity)).orElse(false)) {
                    total += 0.9;
                    count++;
                }
                continue;
            }
            for (RelationAssertion assertion : matchingAssertions(atom.predicate(), graph)) {
                if (matchBinding(atom, assertion, binding) != null) {
                    total += assertion.confidence();
                    count++;
                    break;
                }
            }
        }
        if (count == 0) {
            return rule.confidence();
        }
        return total / count;
    }

    private List<String> matchedAntecedentEvidence(RuleFrame rule,
                                                   SymbolId binding,
                                                   KnowledgeBase graph) {
        List<String> evidence = new ArrayList<>();
        for (RuleAtom atom : rule.antecedents()) {
            if ("rdf:type".equals(atom.predicate())) {
                graph.findEntity(binding).ifPresent(entity -> {
                    if (matchesType(atom, entity)) {
                        evidence.add(entity.id() + " rdf:type " + atom.object().value());
                    }
                });
                continue;
            }
            for (RelationAssertion assertion : matchingAssertions(atom.predicate(), graph)) {
                if (matchBinding(atom, assertion, binding) != null) {
                    evidence.add(assertion.toString());
                    break;
                }
            }
        }
        return evidence;
    }

    private List<String> matchedAntecedentAssertionIds(RuleFrame rule,
                                                       SymbolId binding,
                                                       KnowledgeBase graph) {
        List<String> ids = new ArrayList<>();
        for (RuleAtom atom : rule.antecedents()) {
            AssertionRecord matched = matchedAntecedentRecord(atom, binding, graph);
            if (matched != null) {
                ids.add(matched.id());
            }
        }
        return ids;
    }

    private AssertionRecord matchedAntecedentRecord(RuleAtom atom,
                                                    SymbolId binding,
                                                    KnowledgeBase graph) {
        if (atom == null || graph == null) {
            return null;
        }
        SymbolId subject = resolveTermForFilter(atom.subject(), binding);
        SymbolId object = resolveTermForFilter(atom.object(), binding);
        if ("rdf:type".equals(atom.predicate())) {
            return graph.findAssertionRecords(AssertionFilter.of(subject, atom.predicate(), object, Set.of())).stream()
                    .findFirst()
                    .orElse(null);
        }
        return graph.findAssertionRecords(AssertionFilter.of(subject, atom.predicate(), object, Set.of())).stream()
                .findFirst()
                .orElse(null);
    }

    private SymbolId resolveTermForFilter(RuleTerm term, SymbolId binding) {
        if (term == null) {
            return null;
        }
        if (term.isVariable()) {
            return GROUND_RULE_BINDING.equals(binding) ? null : binding;
        }
        if (term.value() == null || term.value().isBlank()) {
            return null;
        }
        return new SymbolId(term.value());
    }

    private boolean matchesType(RuleAtom atom, EntityNode entity) {
        if (entity == null || !"rdf:type".equals(atom.predicate())) {
            return false;
        }
        String expected = normalizeTypeValue(atom.object());
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return entity.conceptTypes().stream()
                .map(this::normalizeTypeToken)
                .anyMatch(type -> type.equalsIgnoreCase(expected));
    }

    private List<RelationAssertion> matchingAssertions(String predicate, KnowledgeBase graph) {
        if (predicate == null || graph == null) {
            return List.of();
        }
        List<RelationAssertion> direct = graph.findByPredicate(predicate);
        if (!direct.isEmpty()) {
            return direct;
        }
        String normalized = normalizePredicateToken(predicate);
        if (normalized == null) {
            return List.of();
        }
        List<RelationAssertion> matches = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (normalized.equals(normalizePredicateToken(assertion.predicate()))) {
                matches.add(assertion);
            }
        }
        return matches;
    }

    private boolean predicateEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        String normalizedLeft = normalizePredicateToken(left);
        String normalizedRight = normalizePredicateToken(right);
        return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeTypeValue(RuleTerm term) {
        if (term == null || term.isVariable()) {
            return null;
        }
        return normalizeTypeToken(term.value());
    }

    private String normalizeTypeToken(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("concept:")) {
            return value.substring("concept:".length());
        }
        if (value.startsWith("entity:")) {
            return value.substring("entity:".length());
        }
        int hashIdx = value.lastIndexOf('#');
        int slashIdx = value.lastIndexOf('/');
        int sepIdx = Math.max(hashIdx, slashIdx);
        if (sepIdx >= 0 && sepIdx + 1 < value.length()) {
            return value.substring(sepIdx + 1);
        }
        return value;
    }

    private String normalizePredicateToken(String predicate) {
        if (predicate == null) {
            return null;
        }
        int hashIdx = predicate.lastIndexOf('#');
        int slashIdx = predicate.lastIndexOf('/');
        int sepIdx = Math.max(hashIdx, slashIdx);
        if (sepIdx >= 0 && sepIdx + 1 < predicate.length()) {
            return predicate.substring(sepIdx + 1);
        }
        return predicate;
    }
}
