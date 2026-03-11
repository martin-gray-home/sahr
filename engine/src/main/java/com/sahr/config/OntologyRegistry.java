package com.sahr.config;

import com.sahr.core.OntologyService;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.CachedOntologyService;
import com.sahr.nlp.CompositeTermMapper;
import com.sahr.ontology.LabelLexicalMapper;
import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import com.sahr.ontology.VectorLexicalMapper;
import com.sahr.ontology.OnnxTextVectorizer;
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
        TermMapper mapper = buildTermMapper(config, ontology);
        return new OntologyContext(cached, mapper, ontology);
    }

    private static TermMapper buildTermMapper(EngineConfig config, OWLOntology ontology) {
        TermMapper labelMapper = new LabelLexicalMapper(ontology);
        TermMapper vectorMapper = null;
        if (config.vectorEnabled()) {
            vectorMapper = createVectorMapper(config, ontology);
        }
        if (vectorMapper == null) {
            return labelMapper;
        }
        return new CompositeTermMapper(List.of(labelMapper, vectorMapper));
    }

    private static TermMapper createVectorMapper(EngineConfig config, OWLOntology ontology) {
        String modelPath = config.vectorModelPath();
        String vocabPath = config.vectorVocabPath();
        if (modelPath == null || modelPath.isBlank() || vocabPath == null || vocabPath.isBlank()) {
            logger.warning("Vector mapper enabled but model/vocab paths are missing. Falling back to label mapper.");
            return null;
        }
        try (var modelStream = OntologyRegistry.class.getClassLoader().getResourceAsStream(modelPath);
             var vocabStream = OntologyRegistry.class.getClassLoader().getResourceAsStream(vocabPath)) {
            if (modelStream == null || vocabStream == null) {
                logger.warning("Vector mapper enabled but model/vocab resources not found. Falling back to label mapper.");
                return null;
            }
            OnnxTextVectorizer vectorizer = OnnxTextVectorizer.fromStreams(
                    modelStream,
                    vocabStream,
                    config.vectorMaxTokens()
            );
            return new VectorLexicalMapper(ontology, vectorizer, config.vectorThreshold());
        } catch (Exception e) {
            logger.warning("Vector mapper failed to initialize: " + e.getMessage());
            return null;
        }
    }
}
