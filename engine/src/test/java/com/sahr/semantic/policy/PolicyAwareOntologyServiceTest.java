package com.sahr.semantic.policy;

import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import com.sahr.semantic.importer.OwlAlignmentPipeline;
import com.sahr.semantic.importer.OwlAlignmentResult;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAwareOntologyServiceTest {

    @Test
    void usesPolicyDecisionsForPropertySemantics() {
        OWLOntology ontology = OntologyLoader.loadFromClasspath(List.of("ontology/test-owl-ingest.ttl"));
        OwlAlignmentResult aligned = OwlAlignmentPipeline.defaultPipeline()
                .run(ontology, "TestPolicy");

        PropertyPolicyRegistry registry = PropertyPolicyRegistry.fromDecisions(aligned.propertyPolicyDecisions());
        PolicyAwareOntologyService service = new PolicyAwareOntologyService(
                new OwlApiOntologyService(ontology),
                registry
        );

        String near = "https://sahr.ai/ontology/relations#near";
        String nearInverse = "https://sahr.ai/ontology/relations#nearInverse";

        assertTrue(service.isSymmetricProperty(near));
        assertTrue(service.isTransitiveProperty(near));
        assertEquals(nearInverse, service.getInverseProperty(near).orElseThrow());

        assertFalse(service.isSymmetricProperty(nearInverse));
        assertFalse(service.isTransitiveProperty(nearInverse));
        assertEquals(near, service.getInverseProperty(nearInverse).orElseThrow());
        assertEquals(
                com.sahr.semantic.model.InferencePolicyStrength.RANKING_HINT,
                service.inversePolicy(nearInverse).orElseThrow().strength()
        );
    }
}
