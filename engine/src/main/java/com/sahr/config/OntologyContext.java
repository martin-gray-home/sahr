package com.sahr.config;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;
import org.semanticweb.owlapi.model.OWLOntology;

public final class OntologyContext {
    private final OntologyService service;
    private final TermMapper termMapper;
    private final OWLOntology ontology;

    public OntologyContext(OntologyService service, TermMapper termMapper, OWLOntology ontology) {
        this.service = service;
        this.termMapper = termMapper;
        this.ontology = ontology;
    }

    public OntologyService service() {
        return service;
    }

    public TermMapper termMapper() {
        return termMapper;
    }

    public OWLOntology ontology() {
        return ontology;
    }
}
