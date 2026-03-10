package com.sahr.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public final class EngineConfig {
    private static final Logger logger = Logger.getLogger(EngineConfig.class.getName());

    private final List<String> ontologyIds;
    private final Map<String, List<String>> ontologyResources;
    private final List<String> headIds;
    private final Map<String, List<String>> predicateAliases;
    private final boolean vectorEnabled;
    private final String vectorModelPath;
    private final String vectorVocabPath;
    private final double vectorThreshold;
    private final int vectorMaxTokens;

    private EngineConfig(List<String> ontologyIds,
                         Map<String, List<String>> ontologyResources,
                         List<String> headIds,
                         Map<String, List<String>> predicateAliases,
                         boolean vectorEnabled,
                         String vectorModelPath,
                         String vectorVocabPath,
                         double vectorThreshold,
                         int vectorMaxTokens) {
        this.ontologyIds = List.copyOf(ontologyIds);
        this.ontologyResources = Map.copyOf(ontologyResources);
        this.headIds = List.copyOf(headIds);
        this.predicateAliases = Map.copyOf(predicateAliases);
        this.vectorEnabled = vectorEnabled;
        this.vectorModelPath = vectorModelPath;
        this.vectorVocabPath = vectorVocabPath;
        this.vectorThreshold = vectorThreshold;
        this.vectorMaxTokens = vectorMaxTokens;
    }

    public List<String> ontologyIds() {
        return ontologyIds;
    }

    public Map<String, List<String>> ontologyResources() {
        return ontologyResources;
    }

    public List<String> headIds() {
        return headIds;
    }

    public Map<String, List<String>> predicateAliases() {
        return predicateAliases;
    }

    public boolean vectorEnabled() {
        return vectorEnabled;
    }

    public String vectorModelPath() {
        return vectorModelPath;
    }

    public String vectorVocabPath() {
        return vectorVocabPath;
    }

    public double vectorThreshold() {
        return vectorThreshold;
    }

    public int vectorMaxTokens() {
        return vectorMaxTokens;
    }

    public static EngineConfig loadFromClasspath(String resourcePath) {
        Properties properties = new Properties();
        try (InputStream stream = EngineConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing config resource: " + resourcePath);
            }
            properties.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config resource: " + resourcePath, e);
        }

        List<String> ontologyIds = splitList(properties.getProperty("ontologies"));
        List<String> headIds = splitList(properties.getProperty("heads"));
        Map<String, List<String>> predicateAliases = parsePredicateAliases(properties.getProperty("predicate.aliases"));
        boolean vectorEnabled = Boolean.parseBoolean(properties.getProperty("vector.enabled", "false"));
        String vectorModelPath = properties.getProperty("vector.model", "");
        String vectorVocabPath = properties.getProperty("vector.vocab", "");
        double vectorThreshold = parseDouble(properties.getProperty("vector.threshold"), 0.6);
        int vectorMaxTokens = parseInt(properties.getProperty("vector.maxTokens"), 32);

        Map<String, List<String>> ontologyResources = new LinkedHashMap<>();
        for (String id : ontologyIds) {
            String key = "ontology." + id + ".resources";
            List<String> resources = splitList(properties.getProperty(key));
            if (resources.isEmpty()) {
                throw new IllegalArgumentException("Missing resources for ontology pack: " + id);
            }
            ontologyResources.put(id, resources);
        }

        logger.info(() -> "Loaded engine config: ontologies=" + ontologyIds + ", heads=" + headIds);
        return new EngineConfig(ontologyIds, ontologyResources, headIds, predicateAliases,
                vectorEnabled, vectorModelPath, vectorVocabPath, vectorThreshold, vectorMaxTokens);
    }

    public static EngineConfig fromValues(List<String> ontologyIds,
                                          Map<String, List<String>> ontologyResources,
                                          List<String> headIds) {
        return new EngineConfig(ontologyIds, ontologyResources, headIds, Map.of(),
                false, "", "", 0.6, 32);
    }

    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",");
        List<String> results = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                results.add(trimmed);
            }
        }
        return results;
    }

    private static Map<String, List<String>> parsePredicateAliases(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String from = parts[0].trim();
            String to = parts[1].trim();
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            aliases.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
        }
        return aliases;
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
