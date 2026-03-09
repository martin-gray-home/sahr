package com.sahr.core;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBase {
    void addEntity(EntityNode entity);

    void addAssertion(RelationAssertion assertion);

    List<RelationAssertion> findBySubject(SymbolId subject);

    List<RelationAssertion> findByPredicate(String predicate);

    List<RelationAssertion> findByObject(SymbolId object);

    List<RelationAssertion> getAllAssertions();

    Optional<EntityNode> findEntity(SymbolId id);

    List<EntityNode> getAllEntities();
}
