package com.sahr.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WorkingMemoryAttentionScorer {
    private static final double NEUTRAL_MEMORY_FOCUS = 1.0;
    private static final double BASELINE_MEMORY_FOCUS = 0.85;

    MemoryFocusResult score(HeadContext context, ReasoningCandidate candidate) {
        if (context == null || candidate == null) {
            return MemoryFocusResult.neutral();
        }
        WorkingMemory memory = context.workingMemory();
        if (memory == null || isEmpty(memory)) {
            return MemoryFocusResult.neutral();
        }
        CandidateSignal signal = CandidateSignal.from(candidate);
        double activeEntityFocus = scoreActiveEntities(memory.activeEntityOrder(), signal.entities());
        double recentAssertionFocus = scoreRecentAssertions(memory.recentAssertions(), signal);
        double strongestSignal = Math.max(activeEntityFocus, recentAssertionFocus);
        double focus = BASELINE_MEMORY_FOCUS + ((1.0 - BASELINE_MEMORY_FOCUS) * strongestSignal);
        return new MemoryFocusResult(clamp(focus), activeEntityFocus, recentAssertionFocus);
    }

    private boolean isEmpty(WorkingMemory memory) {
        return memory.activeEntities().isEmpty()
                && memory.recentAssertions().isEmpty()
                && memory.goalStack().isEmpty();
    }

    private double scoreActiveEntities(List<SymbolId> activeOrder, Set<SymbolId> entities) {
        if (activeOrder == null || activeOrder.isEmpty() || entities.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (int i = 0; i < activeOrder.size(); i++) {
            if (!entities.contains(activeOrder.get(i))) {
                continue;
            }
            double recency = 1.0 - Math.min(0.8, i * 0.15);
            best = Math.max(best, clamp(recency));
        }
        return best;
    }

    private double scoreRecentAssertions(List<RelationAssertion> assertions, CandidateSignal signal) {
        if (assertions == null || assertions.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (RelationAssertion assertion : assertions) {
            if (assertion == null) {
                continue;
            }
            if (signal.containsTriple(assertion.subject().value(), assertion.predicate(), assertion.object().value())) {
                return 1.0;
            }
            if (signal.entities().contains(assertion.subject()) || signal.entities().contains(assertion.object())) {
                best = Math.max(best, 0.75);
            }
            if (signal.predicates().contains(assertion.predicate())) {
                best = Math.max(best, 0.6);
            }
        }
        return best;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    record MemoryFocusResult(double focus, double activeEntityFocus, double recentAssertionFocus) {
        static MemoryFocusResult neutral() {
            return new MemoryFocusResult(NEUTRAL_MEMORY_FOCUS, 0.0, 0.0);
        }
    }

    private static final class CandidateSignal {
        private final Set<SymbolId> entities;
        private final Set<String> predicates;
        private final Set<String> triples;

        private CandidateSignal(Set<SymbolId> entities, Set<String> predicates, Set<String> triples) {
            this.entities = entities;
            this.predicates = predicates;
            this.triples = triples;
        }

        static CandidateSignal from(ReasoningCandidate candidate) {
            Set<SymbolId> entities = new HashSet<>();
            Set<String> predicates = new HashSet<>();
            Set<String> triples = new HashSet<>();

            if (candidate.payload() instanceof SymbolId symbolId) {
                entities.add(symbolId);
            }
            if (candidate.payload() instanceof RelationAssertion assertion) {
                addAssertion(assertion, entities, predicates, triples);
            }
            if (candidate.payload() instanceof QueryResult result) {
                for (RelationAssertion fact : result.facts()) {
                    addAssertion(fact, entities, predicates, triples);
                }
                for (QueryBinding binding : result.bindings()) {
                    if (binding.subject() != null) {
                        entities.add(binding.subject());
                    }
                    if (binding.object() != null) {
                        entities.add(binding.object());
                    }
                    if (binding.answer() != null) {
                        entities.add(binding.answer());
                    }
                    if (binding.predicate() != null && !binding.predicate().isBlank()) {
                        predicates.add(binding.predicate());
                    }
                    if (binding.evidence() != null) {
                        parseTriple(binding.evidence()).ifPresent(triple -> {
                            entities.add(new SymbolId(triple.subject()));
                            entities.add(new SymbolId(triple.object()));
                            predicates.add(triple.predicate());
                            triples.add(triple.key());
                        });
                    }
                }
            }
            for (String evidence : candidate.evidence()) {
                parseTriple(evidence).ifPresent(triple -> {
                    entities.add(new SymbolId(triple.subject()));
                    entities.add(new SymbolId(triple.object()));
                    predicates.add(triple.predicate());
                    triples.add(triple.key());
                });
            }
            return new CandidateSignal(Set.copyOf(entities), Set.copyOf(predicates), Set.copyOf(triples));
        }

        Set<SymbolId> entities() {
            return entities;
        }

        Set<String> predicates() {
            return predicates;
        }

        boolean containsTriple(String subject, String predicate, String object) {
            return triples.contains(subject + "|" + predicate + "|" + object);
        }

        private static void addAssertion(RelationAssertion assertion,
                                         Set<SymbolId> entities,
                                         Set<String> predicates,
                                         Set<String> triples) {
            if (assertion == null) {
                return;
            }
            entities.add(assertion.subject());
            entities.add(assertion.object());
            predicates.add(assertion.predicate());
            triples.add(assertion.subject().value() + "|" + assertion.predicate() + "|" + assertion.object().value());
        }

        private static java.util.Optional<Triple> parseTriple(String text) {
            if (text == null || text.isBlank()) {
                return java.util.Optional.empty();
            }
            String[] parts = text.trim().split("\\s+");
            if (parts.length < 3) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Triple(parts[0], parts[1], parts[2]));
        }
    }

    private record Triple(String subject, String predicate, String object) {
        String key() {
            return subject + "|" + predicate + "|" + object;
        }
    }
}
