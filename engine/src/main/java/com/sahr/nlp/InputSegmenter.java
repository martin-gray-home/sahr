package com.sahr.nlp;

import java.util.ArrayList;
import java.util.List;

public final class InputSegmenter {
    public List<String> segment(String input) {
        if (input == null) {
            return List.of();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] parts = trimmed.split("(?<=[.!?])\\s+");
        List<String> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String segment = part.trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }
}
