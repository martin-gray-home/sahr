package com.sahr.nlp;

import com.sahr.core.AssertionLayer;
import com.sahr.core.SymbolId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Statement {
    private final SymbolId subject;
    private final SymbolId object;
    private final String predicate;
    private final Set<String> subjectTypes;
    private final Set<String> objectTypes;
    private final boolean objectIsConcept;
    private final double confidence;
    private final List<Statement> additionalStatements;
    private final AssertionLayer layer;

    public Statement(SymbolId subject,
                     SymbolId object,
                     String predicate,
                     Set<String> subjectTypes,
                     Set<String> objectTypes,
                     boolean objectIsConcept,
                     double confidence) {
        this(subject, object, predicate, subjectTypes, objectTypes, objectIsConcept, confidence, List.of(), AssertionLayer.SURFACE);
    }

    public Statement(SymbolId subject,
                     SymbolId object,
                     String predicate,
                     Set<String> subjectTypes,
                     Set<String> objectTypes,
                     boolean objectIsConcept,
                     double confidence,
                     List<Statement> additionalStatements) {
        this(subject, object, predicate, subjectTypes, objectTypes, objectIsConcept, confidence, additionalStatements, AssertionLayer.SURFACE);
    }

    public Statement(SymbolId subject,
                     SymbolId object,
                     String predicate,
                     Set<String> subjectTypes,
                     Set<String> objectTypes,
                     boolean objectIsConcept,
                     double confidence,
                     List<Statement> additionalStatements,
                     AssertionLayer layer) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.object = Objects.requireNonNull(object, "object");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.subjectTypes = Collections.unmodifiableSet(Objects.requireNonNull(subjectTypes, "subjectTypes"));
        this.objectTypes = Collections.unmodifiableSet(Objects.requireNonNull(objectTypes, "objectTypes"));
        this.objectIsConcept = objectIsConcept;
        this.confidence = confidence;
        this.additionalStatements = Collections.unmodifiableList(
                additionalStatements == null ? List.of() : additionalStatements);
        this.layer = Objects.requireNonNull(layer, "layer");
    }

    public SymbolId subject() {
        return subject;
    }

    public SymbolId object() {
        return object;
    }

    public String predicate() {
        return predicate;
    }

    public Set<String> subjectTypes() {
        return subjectTypes;
    }

    public Set<String> objectTypes() {
        return objectTypes;
    }

    public boolean objectIsConcept() {
        return objectIsConcept;
    }

    public double confidence() {
        return confidence;
    }

    public List<Statement> additionalStatements() {
        return additionalStatements;
    }

    public AssertionLayer layer() {
        return layer;
    }
}
