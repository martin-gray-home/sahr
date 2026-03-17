package com.sahr.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AssertionRecordTest {
    @Test
    void buildsAssertionRecordWithLayerAndProvenance() {
        AssertionProvenance provenance = new AssertionProvenance(
                AssertionSource.USER,
                "statement",
                3L,
                Instant.EPOCH,
                AssertionMode.ASSERTED,
                List.of("support-1"),
                "source-1",
                ContradictionStatus.UNKNOWN
        );
        AssertionRecord record = new AssertionRecord(
                "assertion-1",
                new SymbolId("entity:hat"),
                "rdf:type",
                new SymbolId("concept:green"),
                0.9,
                AssertionLayer.CANONICAL,
                provenance
        );

        assertEquals("assertion-1", record.id());
        assertEquals(AssertionLayer.CANONICAL, record.layer());
        assertEquals("entity:hat", record.subject().value());
        assertEquals("concept:green", record.object().value());
        assertNotNull(record.toRelationAssertion());
        assertEquals("rdf:type", record.toRelationAssertion().predicate());
    }
}
