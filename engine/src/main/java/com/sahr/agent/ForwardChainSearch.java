package com.sahr.agent;

import com.sahr.core.KnowledgeBase;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleFrame;
import com.sahr.core.RuleFrames;
import com.sahr.core.SymbolId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToDoubleFunction;

final class ForwardChainSearch {
    interface Formatter {
        String formatAssertionSentence(RelationAssertion assertion);

        String formatRuleSentence(RuleFrame rule);

        String localName(String predicate);

        Boolean booleanConcept(SymbolId id);
    }

    record ChainResult(List<String> sentences, double score) {}

    private final KnowledgeBase graph;
    private final AliasBridge aliasBridge;
    private final Formatter formatter;
    private final ToDoubleFunction<String> predicateDynamismScore;
    private final ToDoubleFunction<RelationAssertion> assertionSpecificity;
    private final ToDoubleFunction<RuleFrame> ruleSpecificity;
    private final Object indexLock = new Object();
    private long indexVersion = -1;
    private Map<SymbolId, List<RelationAssertion>> assertionsBySubject = new HashMap<>();
    private Map<SymbolId, List<RuleFrame>> rulesByAntecedent = new HashMap<>();

    ForwardChainSearch(KnowledgeBase graph,
                       AliasBridge aliasBridge,
                       Formatter formatter,
                       ToDoubleFunction<String> predicateDynamismScore,
                       ToDoubleFunction<RelationAssertion> assertionSpecificity,
                       ToDoubleFunction<RuleFrame> ruleSpecificity) {
        this.graph = graph;
        this.aliasBridge = aliasBridge;
        this.formatter = formatter;
        this.predicateDynamismScore = predicateDynamismScore;
        this.assertionSpecificity = assertionSpecificity;
        this.ruleSpecificity = ruleSpecificity;
    }

    ChainResult search(SymbolId start, SymbolId target, int maxDepth) {
        List<String> sentences = new ArrayList<>();
        if (start == null || target == null) {
            return new ChainResult(sentences, 0.0);
        }
        refreshIndexesIfNeeded();
        Map<SymbolId, Double> bestScore = new HashMap<>();
        PriorityQueue<ChainStep> queue = new PriorityQueue<>(
                Comparator.<ChainStep>comparingDouble(step -> -step.score)
                        .thenComparingInt(step -> step.depth)
        );
        Set<SymbolId> knownSymbols = aliasBridge.collectKnownSymbols();
        Set<String> temporalPredicates = aliasBridge.temporalPredicateNames();
        queue.add(new ChainStep(start, null, null, null, 0.0));
        bestScore.put(start, 0.0);
        ChainResult bestResult = null;
        while (!queue.isEmpty() && maxDepth > 0) {
            ChainStep current = queue.remove();
            if (current.node.equals(target)) {
                List<String> chain = renderChainSteps(current);
                double chainScore = current.score;
                if (!chain.isEmpty()) {
                    if (bestResult == null || chainScore > bestResult.score()) {
                        bestResult = new ChainResult(chain, chainScore);
                    }
                }
                continue;
            }
            if (current.depth >= maxDepth) {
                continue;
            }
            for (SymbolId next : aliasBridge.aliasNodes(current.node, knownSymbols)) {
                enqueueChainStep(queue, bestScore, current, next, 0.05, null, null);
            }
            for (SymbolId next : aliasBridge.typeNodes(current.node, knownSymbols)) {
                enqueueChainStep(queue, bestScore, current, next, 0.15, null, null);
            }
            for (SymbolId next : aliasBridge.temporalBridgeNodes(current.node, temporalPredicates)) {
                enqueueChainStep(queue, bestScore, current, next, 0.25, null, null);
            }
            List<RelationAssertion> assertions = assertionsBySubject.get(current.node);
            if (assertions != null) {
                for (RelationAssertion assertion : assertions) {
                    List<SymbolId> nextNodes = nextNodesFromAssertion(current.node, assertion);
                    for (SymbolId next : nextNodes) {
                        double stepScore = assertionSpecificity.applyAsDouble(assertion)
                                + predicateDynamismScore.applyAsDouble(formatter.localName(assertion.predicate()));
                        enqueueChainStep(queue, bestScore, current, next, stepScore, assertion, null);
                    }
                }
            }
            List<RuleFrame> rules = rulesByAntecedent.get(current.node);
            if (rules != null) {
                for (RuleFrame rule : rules) {
                    List<SymbolId> nextNodes = nextNodesFromRule(current.node, rule);
                    for (SymbolId next : nextNodes) {
                        double stepScore = ruleSpecificity.applyAsDouble(rule)
                                + predicateDynamismScore.applyAsDouble(RuleFrames.legacyConsequent(rule)
                                .map(consequent -> formatter.localName(consequent.predicate()))
                                .orElse(""));
                        enqueueChainStep(queue, bestScore, current, next, stepScore, null, rule);
                    }
                }
            }
        }
        return bestResult == null ? new ChainResult(sentences, 0.0) : bestResult;
    }

    private void enqueueChainStep(PriorityQueue<ChainStep> queue,
                                  Map<SymbolId, Double> bestScore,
                                  ChainStep current,
                                  SymbolId next,
                                  double stepScore,
                                  RelationAssertion assertion,
                                  RuleFrame rule) {
        if (next == null) {
            return;
        }
        double score = current.score + stepScore;
        Double existing = bestScore.get(next);
        if (existing != null && existing >= score) {
            return;
        }
        bestScore.put(next, score);
        queue.add(new ChainStep(next, current, assertion, rule, score));
    }

    private List<SymbolId> nextNodesFromAssertion(SymbolId node, RelationAssertion assertion) {
        if (node == null || assertion == null) {
            return List.of();
        }
        if (!assertion.subject().equals(node)) {
            return List.of();
        }
        SymbolId object = assertion.object();
        if (formatter.booleanConcept(object) != null) {
            return List.of();
        }
        if (object == null || object.equals(node)) {
            return List.of();
        }
        return List.of(object);
    }

    private List<SymbolId> nextNodesFromRule(SymbolId node, RuleFrame rule) {
        if (node == null || rule == null) {
            return List.of();
        }
        RelationAssertion antecedent = RuleFrames.legacyAntecedent(rule).orElse(null);
        RelationAssertion consequent = RuleFrames.legacyConsequent(rule).orElse(null);
        if (antecedent == null || consequent == null) {
            return List.of();
        }
        if (!antecedent.subject().equals(node) && !antecedent.object().equals(node)) {
            return List.of();
        }
        List<SymbolId> nextNodes = new ArrayList<>();
        addConsequentNode(consequent.subject(), node, nextNodes);
        addConsequentNode(consequent.object(), node, nextNodes);
        return nextNodes;
    }

    private void addConsequentNode(SymbolId candidate, SymbolId current, List<SymbolId> nextNodes) {
        if (candidate == null || candidate.equals(current)) {
            return;
        }
        if (formatter.booleanConcept(candidate) != null) {
            return;
        }
        nextNodes.add(candidate);
    }

    private List<String> renderChainSteps(ChainStep end) {
        ArrayDeque<String> stack = new ArrayDeque<>();
        ChainStep current = end;
        while (current != null && current.parent != null) {
            if (current.assertion != null) {
                stack.addFirst(formatter.formatAssertionSentence(current.assertion));
            } else if (current.rule != null) {
                stack.addFirst(formatter.formatRuleSentence(current.rule));
            }
            current = current.parent;
        }
        return new ArrayList<>(stack);
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
            assertionsBySubject = new HashMap<>();
            rulesByAntecedent = new HashMap<>();
            for (RelationAssertion assertion : graph.getAllAssertions()) {
                if (assertion == null || assertion.subject() == null) {
                    continue;
                }
                assertionsBySubject
                        .computeIfAbsent(assertion.subject(), key -> new ArrayList<>())
                        .add(assertion);
            }
            for (RuleFrame rule : graph.getAllRuleFrames()) {
                if (rule == null) {
                    continue;
                }
                RelationAssertion antecedent = RuleFrames.legacyAntecedent(rule).orElse(null);
                if (antecedent == null) {
                    continue;
                }
                SymbolId subject = antecedent.subject();
                SymbolId object = antecedent.object();
                if (subject != null) {
                    rulesByAntecedent
                            .computeIfAbsent(subject, key -> new ArrayList<>())
                            .add(rule);
                }
                if (object != null) {
                    rulesByAntecedent
                            .computeIfAbsent(object, key -> new ArrayList<>())
                            .add(rule);
                }
            }
            indexVersion = currentVersion;
        }
    }

    private static final class ChainStep {
        private final SymbolId node;
        private final ChainStep parent;
        private final RelationAssertion assertion;
        private final RuleFrame rule;
        private final int depth;
        private final double score;

        private ChainStep(SymbolId node, ChainStep parent, RelationAssertion assertion, RuleFrame rule, double score) {
            this.node = node;
            this.parent = parent;
            this.assertion = assertion;
            this.rule = rule;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.score = score;
        }
    }
}
