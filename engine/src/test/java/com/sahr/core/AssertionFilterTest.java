package com.sahr.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssertionFilterTest {
    @Test
    void filtersByLayer() {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        AssertionProvenance provenance = new AssertionProvenance(
                AssertionSource.USER,
                "statement",
                1L,
                Instant.EPOCH,
                AssertionMode.ASSERTED,
                List.of(),
                null,
                null,
                ContradictionStatus.UNKNOWN
        );
        graph.addAssertionRecord(new AssertionRecord(
                "a1",
                new SymbolId("entity:hat"),
                "rdf:type",
                new SymbolId("concept:green"),
                0.9,
                AssertionLayer.CANONICAL,
                provenance
        ));
        graph.addAssertionRecord(new AssertionRecord(
                "a2",
                new SymbolId("entity:hat"),
                "hasAttribute",
                new SymbolId("entity:green"),
                0.9,
                AssertionLayer.DERIVED_HELPER,
                provenance
        ));

        AssertionFilter filter = AssertionFilter.of(
                new SymbolId("entity:hat"),
                null,
                null,
                Set.of(AssertionLayer.CANONICAL)
        );
        assertEquals(1, graph.findAssertionRecords(filter).size());
    }
}
