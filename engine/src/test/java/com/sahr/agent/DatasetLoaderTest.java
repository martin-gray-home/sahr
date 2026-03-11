package com.sahr.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetLoaderTest {
    @Test
    void splitsStatementsAndQuestions(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("dataset.txt");
        Files.writeString(file, """
                # Knowledge Statements
                1. The motor failed.
                2) The wheel stopped.

                # Reasoning Questions
                1. What failed?
                2. What stopped?
                """);

        Dataset dataset = DatasetLoader.load(file);

        assertEquals(2, dataset.statements().size());
        assertEquals(2, dataset.questions().size());
        assertTrue(dataset.statements().get(0).contains("motor failed"));
        assertTrue(dataset.questions().get(0).toLowerCase().contains("what failed"));
    }
}
