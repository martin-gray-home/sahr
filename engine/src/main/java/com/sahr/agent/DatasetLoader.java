package com.sahr.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DatasetLoader {
    private DatasetLoader() {
    }

    public static Dataset load(Path path) throws IOException {
        List<String> statements = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        Section section = Section.NONE;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    String normalized = trimmed.toLowerCase(Locale.ROOT);
                    if (normalized.contains("knowledge statements") || normalized.contains("statements")) {
                        section = Section.STATEMENTS;
                    } else if (normalized.contains("reasoning questions") || normalized.contains("questions")) {
                        section = Section.QUESTIONS;
                    }
                    continue;
                }
                String cleaned = stripLeadingEnumeration(trimmed);
                if (cleaned.isBlank()) {
                    continue;
                }
                if (section == Section.NONE) {
                    statements.add(cleaned);
                } else if (section == Section.STATEMENTS) {
                    statements.add(cleaned);
                } else {
                    questions.add(cleaned);
                }
            }
        }
        return new Dataset(statements, questions);
    }

    private static String stripLeadingEnumeration(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int index = 0;
        while (index < trimmed.length() && Character.isDigit(trimmed.charAt(index))) {
            index++;
        }
        if (index == 0) {
            return trimmed;
        }
        if (index < trimmed.length()) {
            char next = trimmed.charAt(index);
            if (next == '.' || next == ')' || next == ':') {
                index++;
            }
        }
        while (index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index))) {
            index++;
        }
        return trimmed.substring(index).trim();
    }

    private enum Section {
        NONE,
        STATEMENTS,
        QUESTIONS
    }
}
