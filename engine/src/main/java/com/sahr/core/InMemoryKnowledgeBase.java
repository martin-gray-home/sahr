package com.sahr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class InMemoryKnowledgeBase implements KnowledgeBase {
    private final Map<SymbolId, EntityNode> entities = new ConcurrentHashMap<>();
    private final List<AssertionRecord> assertionRecords = new ArrayList<>();
    private final List<RuleFrame> ruleFrames = new ArrayList<>();
    private final AtomicLong version = new AtomicLong();

    @Override
    public void addEntity(EntityNode entity) {
        entities.put(entity.id(), entity);
        version.incrementAndGet();
    }

    @Override
    public void addAssertion(RelationAssertion assertion) {
        long nextVersion = version.incrementAndGet();
        AssertionRecord record = new AssertionRecord(
                "legacy-" + nextVersion,
                assertion.subject(),
                assertion.predicate(),
                assertion.object(),
                assertion.confidence(),
                AssertionLayer.INFERRED,
                new AssertionProvenance(
                        AssertionSource.UNKNOWN,
                        "legacy",
                        nextVersion,
                        java.time.Instant.now(),
                        AssertionMode.DERIVED,
                        List.of(),
                        null,
                        null,
                        ContradictionStatus.UNKNOWN
                )
        );
        assertionRecords.add(record);
    }

    @Override
    public void addAssertionRecord(AssertionRecord assertion) {
        assertionRecords.add(assertion);
        version.incrementAndGet();
    }

    @Override
    public void addRule(RuleAssertion rule) {
        addRuleFrame(RuleFrames.fromLegacyRuleAssertion(rule));
    }

    @Override
    public void addRuleFrame(RuleFrame rule) {
        ruleFrames.add(rule);
        version.incrementAndGet();
    }

    @Override
    public List<RelationAssertion> findBySubject(SymbolId subject) {
        return assertionRecords.stream()
                .filter(assertion -> assertion.subject().equals(subject))
                .map(AssertionRecord::toRelationAssertion)
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> findByPredicate(String predicate) {
        return assertionRecords.stream()
                .filter(assertion -> assertion.predicate().equals(predicate))
                .map(AssertionRecord::toRelationAssertion)
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> findByObject(SymbolId object) {
        return assertionRecords.stream()
                .filter(assertion -> assertion.object().equals(object))
                .map(AssertionRecord::toRelationAssertion)
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> getAllAssertions() {
        return assertionRecords.stream()
                .map(AssertionRecord::toRelationAssertion)
                .collect(Collectors.toList());
    }

    @Override
    public List<AssertionRecord> getAssertionRecords() {
        return new ArrayList<>(assertionRecords);
    }

    @Override
    public List<AssertionRecord> findAssertionRecords(AssertionFilter filter) {
        if (filter == null) {
            return getAssertionRecords();
        }
        return assertionRecords.stream()
                .filter(filter::matches)
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleAssertion> getAllRules() {
        return ruleFrames.stream()
                .map(RuleFrames::toLegacyRuleAssertion)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleFrame> getAllRuleFrames() {
        return new ArrayList<>(ruleFrames);
    }

    @Override
    public Optional<EntityNode> findEntity(SymbolId id) {
        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public List<EntityNode> getAllEntities() {
        return new ArrayList<>(entities.values());
    }

    @Override
    public long version() {
        return version.get();
    }
}
