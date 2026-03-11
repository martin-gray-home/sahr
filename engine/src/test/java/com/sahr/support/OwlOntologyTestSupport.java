package com.sahr.support;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.LabelLexicalMapper;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import com.sahr.heads.OntologyHeadDefinition;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;

public final class OwlOntologyTestSupport {
    private static final List<String> RESOURCES = List.of(
            "ontology/head-ontology.ttl",
            "ontology/sahr-relations.ttl",
            "ontology/reasoning-heads.ttl"
    );

    private OwlOntologyTestSupport() {
    }

    public static OWLOntology loadOntology() {
        return OntologyLoader.loadFromClasspath(RESOURCES);
    }

    public static OntologyService buildOntologyService() {
        return new OwlApiOntologyService(loadOntology());
    }

    public static TermMapper buildTermMapper() {
        return new LabelLexicalMapper(loadOntology());
    }

    public static List<OntologyHeadDefinition> buildHeadDefinitions() {
        return OntologyHeadCompiler.compile(loadOntology());
    }
}
