package com.sahr.ontology;

import com.sahr.nlp.CompositeTermMapper;
import com.sahr.nlp.TermMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

class TermMapperProbeTest {
    private static final Path ENGINE_PROPERTIES =
            Path.of("/Users/margray/git/sahr/applications/src/main/resources/sahr/engine.properties");
    private static final Path RESOURCE_ROOT =
            Path.of("/Users/margray/git/sahr/applications/src/main/resources");

    @Test
    void printMappings() throws IOException {
        Assumptions.assumeTrue(shouldRunProbe());
        Properties properties = loadProperties();
        List<String> resources = resolveOntologyResources(properties);
        TermMapper mapper = buildTermMapper(properties, resources);
        String[] tokens = {"man", "woman", "boy", "girl", "person", "people", "human", "agent"};
        for (String token : tokens) {
            System.out.println(token + " -> " + mapper.mapToken(token));
        }
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(ENGINE_PROPERTIES)) {
            properties.load(stream);
        }
        return properties;
    }

    private List<String> resolveOntologyResources(Properties properties) {
        String raw = properties.getProperty("ontologies", "");
        List<String> ids = splitList(raw);
        List<String> resources = new ArrayList<>();
        for (String id : ids) {
            String key = "ontology." + id + ".resources";
            resources.addAll(splitList(properties.getProperty(key, "")));
        }
        return resources;
    }

    private TermMapper buildTermMapper(Properties properties, List<String> resources) throws IOException {
        var ontology = OntologyLoader.loadFromFiles(resolvePaths(resources));
        TermMapper labelMapper = new LabelLexicalMapper(ontology);
        if (!Boolean.parseBoolean(properties.getProperty("vector.enabled", "false"))) {
            return labelMapper;
        }
        String modelPath = properties.getProperty("vector.model", "");
        String vocabPath = properties.getProperty("vector.vocab", "");
        double threshold = Double.parseDouble(properties.getProperty("vector.threshold", "0.6"));
        int maxTokens = Integer.parseInt(properties.getProperty("vector.maxTokens", "32"));
        if (modelPath.isBlank() || vocabPath.isBlank()) {
            return labelMapper;
        }
        Path modelFile = RESOURCE_ROOT.resolve(modelPath);
        Path vocabFile = RESOURCE_ROOT.resolve(vocabPath);
        if (!Files.exists(modelFile) || !Files.exists(vocabFile)) {
            return labelMapper;
        }
        try (InputStream modelStream = Files.newInputStream(modelFile);
             InputStream vocabStream = Files.newInputStream(vocabFile)) {
            OnnxTextVectorizer vectorizer = OnnxTextVectorizer.fromStreams(modelStream, vocabStream, maxTokens);
            TermMapper vectorMapper = new VectorLexicalMapper(ontology, vectorizer, threshold);
            return new CompositeTermMapper(List.of(labelMapper, vectorMapper));
        }
    }

    private List<Path> resolvePaths(List<String> resources) {
        List<Path> paths = new ArrayList<>();
        for (String resource : resources) {
            paths.add(RESOURCE_ROOT.resolve(resource));
        }
        return paths;
    }

    private List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private boolean shouldRunProbe() {
        String flag = System.getProperty("sahr.termmapper.probe");
        if (flag == null || flag.isBlank()) {
            flag = System.getenv("SAHR_TERM_MAPPER_PROBE");
        }
        return Boolean.parseBoolean(flag);
    }
}
