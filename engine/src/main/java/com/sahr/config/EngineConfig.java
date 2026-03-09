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

    private EngineConfig(List<String> ontologyIds,
                         Map<String, List<String>> ontologyResources,
                         List<String> headIds,
                         Map<String, List<String>> predicateAliases) {
        this.ontologyIds = List.copyOf(ontologyIds);
        this.ontologyResources = Map.copyOf(ontologyResources);
        this.headIds = List.copyOf(headIds);
        this.predicateAliases = Map.copyOf(predicateAliases);
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
        return new EngineConfig(ontologyIds, ontologyResources, headIds, predicateAliases);
    }

    public static EngineConfig fromValues(List<String> ontologyIds,
                                          Map<String, List<String>> ontologyResources,
                                          List<String> headIds) {
        return new EngineConfig(ontologyIds, ontologyResources, headIds, Map.of());
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
}
