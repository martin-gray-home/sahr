package com.sahr.agent;

import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.SahrReasoner;
import com.sahr.core.SymbolId;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.heads.OntologyHeadDefinition;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;
import com.sahr.ontology.LabelLexicalMapper;
import com.sahr.ontology.OntologyHeadCompiler;
import com.sahr.ontology.OntologyLoader;
import com.sahr.ontology.OwlApiOntologyService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WordNetEntityTypeStorageTest {
    private static final String PERSON_SYNSET = "https://en-word.net/id/oewn-00007846-n";
    private static final String MAN_SYNSET = "https://en-word.net/id/oewn-10306910-n";
    private static final String WOMAN_SYNSET = "https://en-word.net/id/oewn-10807146-n";
    private static final String BOY_SYNSET = "https://en-word.net/id/oewn-10305010-n";
    private static final String HAT_SYNSET = "https://en-word.net/id/oewn-03502782-n";

    @Test
    void storesSynsetTypesForProbeEntities() throws IOException {
        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();
        OWLOntology ontology = loadOntology();
        OwlApiOntologyService ontologyService = new OwlApiOntologyService(ontology);
        SahrReasoner reasoner = buildReasoner(ontology);
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        TermMapper mapper = new LabelLexicalMapper(ontology);

        SahrAgent agent = new SahrAgent(graph, ontologyService, reasoner, parser, statementParser, mapper);

        for (String statement : probeStatements()) {
            agent.handle(statement);
        }

        assertEntityHasType(graph, "entity:man", MAN_SYNSET);
        assertEntityHasType(graph, "entity:woman", WOMAN_SYNSET);
        assertEntityHasType(graph, "entity:boy", BOY_SYNSET);
        assertEntityHasType(graph, "entity:hat", HAT_SYNSET);

        System.out.println("entity:man types=" + getTypes(graph, "entity:man"));
        System.out.println("entity:woman types=" + getTypes(graph, "entity:woman"));
        System.out.println("entity:boy types=" + getTypes(graph, "entity:boy"));
        System.out.println("entity:hat types=" + getTypes(graph, "entity:hat"));
        System.out.println("person synset=" + PERSON_SYNSET);
    }

    private void assertEntityHasType(InMemoryKnowledgeBase graph, String entityId, String expectedType) {
        Set<String> types = getTypes(graph, entityId);
        assertTrue(types.contains(expectedType), entityId + " missing expected synset type: " + expectedType + " types=" + types);
    }

    private Set<String> getTypes(InMemoryKnowledgeBase graph, String entityId) {
        return graph.findEntity(new SymbolId(entityId))
                .map(entity -> entity.conceptTypes())
                .orElse(Set.of());
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

    private OWLOntology loadOntology() throws IOException {
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
                if (resourceTrimmed.isBlank()) {
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
}
