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
    private final List<RelationAssertion> assertions = new ArrayList<>();
    private final List<RuleAssertion> rules = new ArrayList<>();
    private final AtomicLong version = new AtomicLong();

    @Override
    public void addEntity(EntityNode entity) {
        entities.put(entity.id(), entity);
        version.incrementAndGet();
    }

    @Override
    public void addAssertion(RelationAssertion assertion) {
        assertions.add(assertion);
        version.incrementAndGet();
    }

    @Override
    public void addRule(RuleAssertion rule) {
        rules.add(rule);
        version.incrementAndGet();
    }

    @Override
    public List<RelationAssertion> findBySubject(SymbolId subject) {
        return assertions.stream()
                .filter(assertion -> assertion.subject().equals(subject))
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> findByPredicate(String predicate) {
        return assertions.stream()
                .filter(assertion -> assertion.predicate().equals(predicate))
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> findByObject(SymbolId object) {
        return assertions.stream()
                .filter(assertion -> assertion.object().equals(object))
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationAssertion> getAllAssertions() {
        return new ArrayList<>(assertions);
    }

    @Override
    public List<RuleAssertion> getAllRules() {
        return new ArrayList<>(rules);
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
