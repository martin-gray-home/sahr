package com.sahr.ontology;

import java.util.Locale;

final class HashingVectorizer implements TextVectorizer {
    private final int dimensions;

    HashingVectorizer(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public float[] vectorize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new float[dimensions];
        }
        String normalized = normalize(raw);
        float[] vector = new float[dimensions];
        if (normalized.isBlank()) {
            return vector;
        }
        char[] chars = normalized.toCharArray();
        if (chars.length < 3) {
            addHash(vector, normalized);
            normalize(vector);
            return vector;
        }
        for (int i = 0; i <= chars.length - 3; i++) {
            String ngram = new String(chars, i, 3);
            addHash(vector, ngram);
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private void addHash(float[] vector, String token) {
        int hash = token.hashCode();
        int idx = Math.floorMod(hash, dimensions);
        int sign = (hash & 1) == 0 ? 1 : -1;
        vector[idx] += sign;
    }

    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0.0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    private String normalize(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        trimmed = trimmed.replaceAll("[^a-z0-9_\\s]", "");
        return trimmed.replaceAll("\\s+", "_");
    }
}
