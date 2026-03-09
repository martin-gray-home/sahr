package com.sahr.ontology;

import com.sahr.core.OntologyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedOntologyServiceTest {
    @Test
    void cachesDelegateResponses() {
        InMemoryOntologyService base = new InMemoryOntologyService();
        base.addSubclass("child", "parent");
        base.addSymmetricProperty("relatedTo");
        base.addTransitiveProperty("locatedIn");
        base.addInverseProperty("contains", "inside");

        OntologyService cached = new CachedOntologyService(base);

        assertTrue(cached.isSubclassOf("child", "parent"));
        assertTrue(cached.isSymmetricProperty("relatedTo"));
        assertTrue(cached.isTransitiveProperty("locatedIn"));
        assertEquals("inside", cached.getInverseProperty("contains").orElseThrow());

        assertTrue(cached.isSubclassOf("child", "parent"));
        assertTrue(cached.isSymmetricProperty("relatedTo"));
        assertTrue(cached.isTransitiveProperty("locatedIn"));
        assertEquals("inside", cached.getInverseProperty("contains").orElseThrow());
    }
}
