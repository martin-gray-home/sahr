package com.sahr.ontology;

import java.util.ArrayList;
import java.util.List;

final class VectorIndex {
    static final class Entry {
        private final String iri;
        private final String label;
        private final float[] vector;

        Entry(String iri, String label, float[] vector) {
            this.iri = iri;
            this.label = label;
            this.vector = vector;
        }

        String iri() {
            return iri;
        }

        String label() {
            return label;
        }

        float[] vector() {
            return vector;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void add(Entry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    int size() {
        return entries.size();
    }

    List<Entry> entries() {
        return entries;
    }
}
