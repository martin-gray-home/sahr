package com.sahr.core;

import java.util.Objects;

public final class HeadContext {
    private final QueryGoal query;
    private final KnowledgeBase graph;
    private final OntologyService ontology;
    private final com.sahr.nlp.Statement statement;
    private final WorkingMemory workingMemory;

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology) {
        this(query, graph, ontology, null, new WorkingMemory());
    }

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology, WorkingMemory workingMemory) {
        this(query, graph, ontology, null, workingMemory);
    }

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology, com.sahr.nlp.Statement statement) {
        this(query, graph, ontology, statement, new WorkingMemory());
    }

    public HeadContext(QueryGoal query, KnowledgeBase graph, OntologyService ontology, com.sahr.nlp.Statement statement, WorkingMemory workingMemory) {
        this.query = Objects.requireNonNull(query, "query");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.statement = statement;
        this.workingMemory = workingMemory == null ? new WorkingMemory() : workingMemory;
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

    public WorkingMemory workingMemory() {
        return workingMemory;
    }
}
