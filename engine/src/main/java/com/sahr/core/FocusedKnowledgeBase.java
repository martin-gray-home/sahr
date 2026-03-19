package com.sahr.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class FocusedKnowledgeBase implements KnowledgeBase {
    private final KnowledgeBase delegate;
    private final Set<String> assertionKeys;
    private final Set<RuleFrame> ruleFrames;
    private final Set<SymbolId> entityIds;
    private final boolean reduced;

    FocusedKnowledgeBase(KnowledgeBase delegate,
                         Set<String> assertionKeys,
                         Set<RuleFrame> ruleFrames,
                         Set<SymbolId> entityIds,
                         boolean reduced) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.assertionKeys = assertionKeys == null ? Set.of() : Set.copyOf(assertionKeys);
        this.ruleFrames = ruleFrames == null ? Set.of() : Set.copyOf(ruleFrames);
        this.entityIds = entityIds == null ? Set.of() : Set.copyOf(entityIds);
        this.reduced = reduced;
    }

    boolean isReduced() {
        return reduced;
    }

    @Override
    public void addEntity(EntityNode entity) {
        delegate.addEntity(entity);
    }

    @Override
    public void addAssertion(RelationAssertion assertion) {
        delegate.addAssertion(assertion);
    }

    @Override
    public void addAssertionRecord(AssertionRecord assertion) {
        delegate.addAssertionRecord(assertion);
    }

    @Override
    public void addRule(RuleAssertion rule) {
        delegate.addRule(rule);
    }

    @Override
    public void addRuleFrame(RuleFrame rule) {
        delegate.addRuleFrame(rule);
    }

    @Override
    public List<RelationAssertion> findBySubject(SymbolId subject) {
        List<RelationAssertion> assertions = delegate.findBySubject(subject);
        return reduced ? filterAssertions(assertions) : assertions;
    }

    @Override
    public List<RelationAssertion> findByPredicate(String predicate) {
        List<RelationAssertion> assertions = delegate.findByPredicate(predicate);
        return reduced ? filterAssertions(assertions) : assertions;
    }

    @Override
    public List<RelationAssertion> findByObject(SymbolId object) {
        List<RelationAssertion> assertions = delegate.findByObject(object);
        return reduced ? filterAssertions(assertions) : assertions;
    }

    @Override
    public List<RelationAssertion> getAllAssertions() {
        List<RelationAssertion> assertions = delegate.getAllAssertions();
        return reduced ? filterAssertions(assertions) : assertions;
    }

    @Override
    public List<AssertionRecord> getAssertionRecords() {
        List<AssertionRecord> records = delegate.getAssertionRecords();
        if (!reduced) {
            return records;
        }
        return records.stream()
                .filter(record -> assertionKeys.contains(assertionKey(record.subject(), record.predicate(), record.object())))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssertionRecord> findAssertionRecords(AssertionFilter filter) {
        List<AssertionRecord> records = delegate.findAssertionRecords(filter);
        if (!reduced) {
            return records;
        }
        return records.stream()
                .filter(record -> assertionKeys.contains(assertionKey(record.subject(), record.predicate(), record.object())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleAssertion> getAllRules() {
        if (!reduced) {
            return delegate.getAllRules();
        }
        return ruleFrames.stream()
                .map(RuleFrames::toLegacyRuleAssertion)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<RuleFrame> getAllRuleFrames() {
        if (!reduced) {
            return delegate.getAllRuleFrames();
        }
        return new ArrayList<>(ruleFrames);
    }

    @Override
    public Optional<EntityNode> findEntity(SymbolId id) {
        if (!reduced || entityIds.contains(id)) {
            return delegate.findEntity(id);
        }
        return Optional.empty();
    }

    @Override
    public List<EntityNode> getAllEntities() {
        if (!reduced) {
            return delegate.getAllEntities();
        }
        List<EntityNode> entities = new ArrayList<>();
        for (SymbolId entityId : entityIds) {
            delegate.findEntity(entityId).ifPresent(entities::add);
        }
        return entities;
    }

    @Override
    public long version() {
        return delegate.version();
    }

    private List<RelationAssertion> filterAssertions(List<RelationAssertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of();
        }
        List<RelationAssertion> filtered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RelationAssertion assertion : assertions) {
            String key = assertionKey(assertion.subject(), assertion.predicate(), assertion.object());
            if (!assertionKeys.contains(key) || !seen.add(key)) {
                continue;
            }
            filtered.add(assertion);
        }
        return filtered;
    }

    static String assertionKey(SymbolId subject, String predicate, SymbolId object) {
        String left = subject == null ? "" : subject.value();
        String right = object == null ? "" : object.value();
        return left + "|" + predicate + "|" + right;
    }
}
