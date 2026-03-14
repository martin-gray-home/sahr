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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

final class DiagnosticTestSupport {
    static final String DATASET_PATH = "datasets/sahr-diagnostic-reasoning.txt";

    static DiagnosticDataset loadDataset() throws IOException {
        List<String> statements = new ArrayList<>();
        List<DiagnosticCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireResource(DATASET_PATH), StandardCharsets.UTF_8))) {
            String line;
            boolean inStatements = false;
            boolean inQuestions = false;
            DiagnosticCaseBuilder builder = null;
            SectionMode mode = SectionMode.NONE;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("# Statements")) {
                    inStatements = true;
                    inQuestions = false;
                    continue;
                }
                if (trimmed.startsWith("# Questions")) {
                    inStatements = false;
                    inQuestions = true;
                    continue;
                }
                if (trimmed.startsWith("## Q")) {
                    if (builder != null) {
                        cases.add(builder.build());
                    }
                    builder = new DiagnosticCaseBuilder(parseIndex(trimmed));
                    mode = SectionMode.NONE;
                    continue;
                }
                if (inStatements) {
                    if (!trimmed.startsWith("#")) {
                        statements.add(trimmed);
                    }
                    continue;
                }
                if (inQuestions && builder != null) {
                    if ("Question".equalsIgnoreCase(trimmed)) {
                        mode = SectionMode.QUESTION;
                        continue;
                    }
                    if ("Expected Answer".equalsIgnoreCase(trimmed)) {
                        mode = SectionMode.EXPECTED_ANSWER;
                        continue;
                    }
                    if (trimmed.startsWith("Expected Reasoning")) {
                        mode = SectionMode.OTHER;
                        continue;
                    }
                    if (mode == SectionMode.QUESTION) {
                        builder.question(trimmed);
                        mode = SectionMode.NONE;
                        continue;
                    }
                    if (mode == SectionMode.EXPECTED_ANSWER) {
                        if (!trimmed.startsWith("#")) {
                            builder.addExpectedAnswer(trimmed);
                        }
                    }
                }
            }
            if (builder != null) {
                cases.add(builder.build());
            }
        }
        return new DiagnosticDataset(statements, cases);
    }

    static SahrAgent buildFullAgent() throws IOException {
        Path repoRoot = locateRepoRoot();
        Path propertiesPath = repoRoot.resolve("applications/src/main/resources/sahr/engine.properties");
        Properties properties = new Properties();
        try (java.io.InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
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
        var ontology = OntologyLoader.loadFromFiles(ontologyPaths);
        var ontologyService = new OwlApiOntologyService(ontology);
        List<OntologyHeadDefinition> definitions = OntologyHeadCompiler.compile(ontology);
        List<com.sahr.core.SymbolicAttentionHead> heads = List.of(new OntologyDefinedHead(definitions));
        SahrReasoner reasoner = new SahrReasoner(heads);
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        TermMapper mapper = new LabelLexicalMapper(ontology);
        return new SahrAgent(new InMemoryKnowledgeBase(), ontologyService, reasoner, parser, statementParser, mapper);
    }

    static List<String> questionLines(DiagnosticDataset dataset) {
        List<String> questions = new ArrayList<>();
        for (DiagnosticCase entry : dataset.cases()) {
            if (entry.question() == null || entry.question().isBlank()) {
                continue;
            }
            questions.add(entry.question());
        }
        return questions;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    static List<String> extractKeywords(String value) {
        String normalized = normalize(value);
        String[] parts = normalized.split(" ");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (STOPWORDS.contains(part)) {
                continue;
            }
            keywords.add(part);
        }
        return keywords;
    }

    static boolean containsAll(String answer, List<String> keywords) {
        for (String keyword : keywords) {
            if (!answer.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    static int parseIndex(String header) {
        String digits = header.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    static java.io.InputStream requireResource(String path) {
        java.io.InputStream stream = DiagnosticTestSupport.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing dataset resource: " + path);
        }
        return stream;
    }

    static Path locateRepoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("applications/src/main/resources/sahr/engine.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repo root from " + Paths.get("").toAbsolutePath());
    }

    enum SectionMode {
        NONE,
        QUESTION,
        EXPECTED_ANSWER,
        OTHER
    }

    record DiagnosticDataset(List<String> statements, List<DiagnosticCase> cases) {
    }

    record DiagnosticCase(int index, String question, List<String> expectedAnswers) {
    }

    private static final class DiagnosticCaseBuilder {
        private final int index;
        private String question;
        private final List<String> expectedAnswers = new ArrayList<>();

        private DiagnosticCaseBuilder(int index) {
            this.index = index;
        }

        private void question(String value) {
            if (question == null || question.isBlank()) {
                question = value;
            }
        }

        private void addExpectedAnswer(String value) {
            expectedAnswers.add(value);
        }

        private DiagnosticCase build() {
            String q = question == null ? "" : question;
            return new DiagnosticCase(index, q, new ArrayList<>(expectedAnswers));
        }
    }

    static final Set<String> STOPWORDS = new HashSet<>();

    static {
        String[] words = {
                "the", "a", "an", "of", "and", "or", "most", "likely", "because",
                "what", "which", "why", "would", "should", "before", "after", "if",
                "that", "this", "these", "those", "to", "be", "is", "are", "was",
                "were", "can", "could", "with", "from", "by", "based", "on",
                "possible", "likely", "best", "fit", "sequence", "events", "explain"
        };
        for (String word : words) {
            STOPWORDS.add(word);
        }
    }
}
