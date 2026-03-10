package com.sahr.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GuardedKnowledgeBase implements KnowledgeBase {
    private final KnowledgeBase delegate;
    private final ReasoningPhaseCoordinator phases;

    public GuardedKnowledgeBase(KnowledgeBase delegate, ReasoningPhaseCoordinator phases) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.phases = Objects.requireNonNull(phases, "phases");
    }

    @Override
    public void addEntity(EntityNode entity) {
        phases.assertUpdatePhase("addEntity");
        delegate.addEntity(entity);
    }

    @Override
    public void addAssertion(RelationAssertion assertion) {
        phases.assertUpdatePhase("addAssertion");
        delegate.addAssertion(assertion);
    }

    @Override
    public List<RelationAssertion> findBySubject(SymbolId subject) {
        return delegate.findBySubject(subject);
    }

    @Override
    public List<RelationAssertion> findByPredicate(String predicate) {
        return delegate.findByPredicate(predicate);
    }

    @Override
    public List<RelationAssertion> findByObject(SymbolId object) {
        return delegate.findByObject(object);
    }

    @Override
    public List<RelationAssertion> getAllAssertions() {
        return delegate.getAllAssertions();
    }

    @Override
    public Optional<EntityNode> findEntity(SymbolId id) {
        return delegate.findEntity(id);
    }

    @Override
    public List<EntityNode> getAllEntities() {
        return delegate.getAllEntities();
    }
}
