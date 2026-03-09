package com.sahr.config;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;

public final class OntologyContext {
    private final OntologyService service;
    private final TermMapper termMapper;

    public OntologyContext(OntologyService service, TermMapper termMapper) {
        this.service = service;
        this.termMapper = termMapper;
    }

    public OntologyService service() {
        return service;
    }

    public TermMapper termMapper() {
        return termMapper;
    }
}
