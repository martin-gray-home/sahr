package com.sahr.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ReasoningPhaseCoordinator {
    private final AtomicReference<ReasoningPhase> phase = new AtomicReference<>(ReasoningPhase.UPDATE);

    public ReasoningPhase current() {
        return phase.get();
    }

    public ReasoningPhase enter(ReasoningPhase next) {
        Objects.requireNonNull(next, "next");
        return phase.getAndSet(next);
    }

    public void restore(ReasoningPhase previous) {
        Objects.requireNonNull(previous, "previous");
        phase.set(previous);
    }

    public void assertUpdatePhase(String operation) {
        if (phase.get() != ReasoningPhase.UPDATE) {
            throw new IllegalStateException("Update operation not allowed during phase " + phase.get()
                    + ": " + operation);
        }
    }

    public void assertReadPhase(String operation) {
        if (phase.get() != ReasoningPhase.READ) {
            throw new IllegalStateException("Read operation not allowed during phase " + phase.get()
                    + ": " + operation);
        }
    }
}
