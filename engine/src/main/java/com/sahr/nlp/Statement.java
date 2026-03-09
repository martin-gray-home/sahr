package com.sahr.nlp;

import com.sahr.core.SymbolId;

import java.util.Collections;
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

    public Statement(SymbolId subject,
                     SymbolId object,
                     String predicate,
                     Set<String> subjectTypes,
                     Set<String> objectTypes,
                     boolean objectIsConcept,
                     double confidence) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.object = Objects.requireNonNull(object, "object");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.subjectTypes = Collections.unmodifiableSet(Objects.requireNonNull(subjectTypes, "subjectTypes"));
        this.objectTypes = Collections.unmodifiableSet(Objects.requireNonNull(objectTypes, "objectTypes"));
        this.objectIsConcept = objectIsConcept;
        this.confidence = confidence;
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
}
