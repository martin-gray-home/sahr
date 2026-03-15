package com.sahr.core;

import com.sahr.ontology.SemanticNodeNormalizer;

import java.util.Objects;

public final class HeadContext {
    private final QueryGoal query;
    private final KnowledgeBase graph;
    private final OntologyService ontology;
    private final com.sahr.nlp.Statement statement;
    private final RuleAssertion rule;
    private final WorkingMemory workingMemory;
    private final com.sahr.nlp.InputFeatures inputFeatures;
    private final SemanticNodeNormalizer semanticNormalizer;

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology) {
        this(query, graph, ontology, null, null, new WorkingMemory(), null, null);
    }

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology, WorkingMemory workingMemory) {
        this(query, graph, ontology, null, null, workingMemory, null, null);
    }

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology, com.sahr.nlp.Statement statement) {
        this(query, graph, ontology, statement, null, new WorkingMemory(), null, null);
    }

    public HeadContext(QueryGoal query,
                       KnowledgeBase graph,
                       OntologyService ontology,
                       com.sahr.nlp.Statement statement,
                       RuleAssertion rule,
                       WorkingMemory workingMemory) {
        this(query, graph, ontology, statement, rule, workingMemory, null, null);
    }

    public HeadContext(QueryGoal query,
                       KnowledgeBase graph,
                       OntologyService ontology,
                       com.sahr.nlp.Statement statement,
                       RuleAssertion rule,
                       WorkingMemory workingMemory,
                       com.sahr.nlp.InputFeatures inputFeatures,
                       SemanticNodeNormalizer semanticNormalizer) {
        this.query = Objects.requireNonNull(query, "query");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.statement = statement;
        this.rule = rule;
        this.workingMemory = workingMemory == null ? new WorkingMemory() : workingMemory;
        this.inputFeatures = inputFeatures;
        this.semanticNormalizer = semanticNormalizer;
    }

    public QueryGoal query() {
        return query;
    }

    public KnowledgeBase graph() {
        return graph;
    }

    public OntologyService ontology() {
        return ontology;
    }

    public java.util.Optional<com.sahr.nlp.Statement> statement() {
        return java.util.Optional.ofNullable(statement);
    }

    public java.util.Optional<RuleAssertion> rule() {
        return java.util.Optional.ofNullable(rule);
    }

    public WorkingMemory workingMemory() {
        return workingMemory;
    }

    public java.util.Optional<com.sahr.nlp.InputFeatures> inputFeatures() {
        return java.util.Optional.ofNullable(inputFeatures);
    }

    public java.util.Optional<SemanticNodeNormalizer> semanticNormalizer() {
        return java.util.Optional.ofNullable(semanticNormalizer);
    }
}
