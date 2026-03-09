package com.sahr.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReasoningTrace {
    private final List<ReasoningTraceEntry> entries = new ArrayList<>();

    public void addEntry(ReasoningTraceEntry entry) {
        entries.add(entry);
    }

    public List<ReasoningTraceEntry> entries() {
        return Collections.unmodifiableList(entries);
    }
}
