package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.heads.OntologyHeadDefinition;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.LabelLexicalMapper;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import com.sahr.ontology.SemanticTypeCompatibilityService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WordNetBridgeDisabledTest {
    @Test
    void whoFilteringWorksWithoutBridgeOntology() throws IOException {
        OWLOntology ontology = loadOntologyWithoutBridge();
        OwlApiOntologyService ontologyService = new OwlApiOntologyService(ontology);
        SahrReasoner reasoner = buildReasoner(ontology);
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        TermMapper mapper = new LabelLexicalMapper(ontology);

        Optional<String> personMapping = mapper.mapToken("person");
        assertTrue(personMapping.isPresent() && personMapping.get().equals("https://en-word.net/id/oewn-00007846-n"),
                "Expected person to map to synset: " + personMapping);

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        SahrAgent agent = new SahrAgent(graph, ontologyService, reasoner, parser, statementParser, mapper);

        SemanticTypeCompatibilityService compatibility = new SemanticTypeCompatibilityService(ontologyService);
        System.out.println("hat->person compat=" + compatibility.isCompatible(
                "https://en-word.net/id/oewn-03502782-n",
                "https://en-word.net/id/oewn-00007846-n"));
        System.out.println("man->person compat=" + compatibility.isCompatible(
                "https://en-word.net/id/oewn-10306910-n",
                "https://en-word.net/id/oewn-00007846-n"));
        System.out.println("woman->person compat=" + compatibility.isCompatible(
                "https://en-word.net/id/oewn-10807146-n",
                "https://en-word.net/id/oewn-00007846-n"));
        System.out.println("boy->person compat=" + compatibility.isCompatible(
                "https://en-word.net/id/oewn-10305010-n",
                "https://en-word.net/id/oewn-00007846-n"));

        for (String statement : probeStatements()) {
            agent.handle(statement);
        }

        System.out.println("entity:man types=" + graph.findEntity(new com.sahr.core.SymbolId("entity:man"))
                .map(entity -> entity.conceptTypes()).orElse(java.util.Set.of()));
        System.out.println("entity:woman types=" + graph.findEntity(new com.sahr.core.SymbolId("entity:woman"))
                .map(entity -> entity.conceptTypes()).orElse(java.util.Set.of()));
        System.out.println("entity:boy types=" + graph.findEntity(new com.sahr.core.SymbolId("entity:boy"))
                .map(entity -> entity.conceptTypes()).orElse(java.util.Set.of()));
        System.out.println("entity:hat types=" + graph.findEntity(new com.sahr.core.SymbolId("entity:hat"))
                .map(entity -> entity.conceptTypes()).orElse(java.util.Set.of()));

        String answer = agent.handle("Who is in the house");
        agent.lastTraceEntry().ifPresent(entry ->
                System.out.println("expectedType=" + entry.query().expectedType()));
        System.out.println("entity:hat types=" + graph.findEntity(new com.sahr.core.SymbolId("entity:hat"))
                .map(entity -> entity.conceptTypes()).orElse(java.util.Set.of()));
        List<String> tokens = normalizeTokens(answer);
        assertTrue(tokens.contains("man"), "Expected man in answer: " + answer);
        assertTrue(tokens.contains("woman"), "Expected woman in answer: " + answer);
        assertTrue(tokens.contains("boy"), "Expected boy in answer: " + answer);
        assertTrue(!tokens.contains("hat"), "Did not expect hat in who answer: " + answer);
    }

    private List<String> probeStatements() {
        return List.of(
                "The man is in the house",
                "The woman is with the man",
                "The boy is with the woman",
                "The man is wearing a hat",
                "The man is on the chair",
                "The hat is under the table"
        );
    }

    private OWLOntology loadOntologyWithoutBridge() throws IOException {
        Path repoRoot = locateRepoRoot();
        Path propertiesPath = repoRoot.resolve("applications/src/main/resources/sahr/engine.properties");
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(propertiesPath)) {
            properties.load(stream);
        }
        List<Path> ontologyPaths = new ArrayList<>();
        String ontologies = properties.getProperty("ontologies", "");
        for (String key : ontologies.split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String resourceKey = "ontology." + trimmed + ".resources";
            String resourceValue = properties.getProperty(resourceKey);
            if (resourceValue == null || resourceValue.isBlank()) {
                continue;
            }
            for (String resource : resourceValue.split(",")) {
                String resourceTrimmed = resource.trim();
                if (resourceTrimmed.isBlank() || resourceTrimmed.contains("sahr-people.ttl")) {
                    continue;
                }
                Path path = repoRoot.resolve("applications/src/main/resources").resolve(resourceTrimmed);
                ontologyPaths.add(path);
            }
        }
        return OntologyLoader.loadFromFiles(ontologyPaths);
    }

    private SahrReasoner buildReasoner(OWLOntology ontology) {
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        List<com.sahr.core.SymbolicAttentionHead> heads = List.of(new OntologyDefinedHead(definitions));
        return new SahrReasoner(heads);
    }

    private Path locateRepoRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("applications/src/main/resources/sahr/engine.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Could not locate repo root from " + Path.of("").toAbsolutePath());
    }

    private List<String> normalizeTokens(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String normalized = answer.toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
