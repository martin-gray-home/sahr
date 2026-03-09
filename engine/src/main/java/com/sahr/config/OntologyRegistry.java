package com.sahr.config;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.CachedOntologyService;
import com.sahr.ontology.LabelLexicalMapper;
import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class OntologyRegistry {
    private static final Logger logger = Logger.getLogger(OntologyRegistry.class.getName());

    private OntologyRegistry() {
    }

    public static OntologyService loadOntology(EngineConfig config) {
        return loadOntologyContext(config).service();
    }

    public static OntologyContext loadOntologyContext(EngineConfig config) {
        List<String> resources = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : config.ontologyResources().entrySet()) {
            resources.addAll(entry.getValue());
        }
        logger.info(() -> "Loading ontology resources: " + resources);
        OWLOntology ontology = OntologyLoader.loadFromClasspath(resources);
        OntologyService delegate = new OwlApiOntologyService(ontology);
        logger.info("Ontology loaded. Building cached view.");
        OntologyService cached = new CachedOntologyService(delegate);
        TermMapper mapper = new LabelLexicalMapper(ontology);
        return new OntologyContext(cached, mapper);
    }
}
