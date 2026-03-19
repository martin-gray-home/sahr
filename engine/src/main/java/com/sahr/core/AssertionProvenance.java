package com.sahr.core;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AssertionProvenance {
    private final AssertionSource source;
    private final String producedBy;
    private final long cycleIndex;
    private final Instant timestamp;
    private final AssertionMode mode;
    private final List<String> supportingAssertionIds;
    private final String normalizedFromId;
    private final InputSegmentOrigin segmentOrigin;
    private final ContradictionStatus contradictionStatus;

    public AssertionProvenance(AssertionSource source,
                               String producedBy,
                               long cycleIndex,
                               Instant timestamp,
                               AssertionMode mode,
                               List<String> supportingAssertionIds,
                               String normalizedFromId,
                               InputSegmentOrigin segmentOrigin,
                               ContradictionStatus contradictionStatus) {
        this.source = Objects.requireNonNull(source, "source");
        this.producedBy = producedBy == null ? "" : producedBy;
        this.cycleIndex = cycleIndex;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.supportingAssertionIds = Collections.unmodifiableList(
                supportingAssertionIds == null ? List.of() : supportingAssertionIds);
        this.normalizedFromId = normalizedFromId;
        this.segmentOrigin = segmentOrigin;
        this.contradictionStatus = Objects.requireNonNull(contradictionStatus, "contradictionStatus");
    }

    public AssertionSource source() {
        return source;
    }

    public String producedBy() {
        return producedBy;
    }

    public long cycleIndex() {
        return cycleIndex;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public AssertionMode mode() {
        return mode;
    }

    public List<String> supportingAssertionIds() {
        return supportingAssertionIds;
    }

    public String normalizedFromId() {
        return normalizedFromId;
    }

    public InputSegmentOrigin segmentOrigin() {
        return segmentOrigin;
    }

    public ContradictionStatus contradictionStatus() {
        return contradictionStatus;
    }

    public AssertionProvenance withNormalizedFromId(String normalizedFromId) {
        return new AssertionProvenance(
                source,
                producedBy,
                cycleIndex,
                timestamp,
                mode,
                supportingAssertionIds,
                normalizedFromId,
                segmentOrigin,
                contradictionStatus
        );
    }

    public AssertionProvenance withSupportingAssertionIds(List<String> supportingAssertionIds) {
        return new AssertionProvenance(
                source,
                producedBy,
                cycleIndex,
                timestamp,
                mode,
                supportingAssertionIds,
                normalizedFromId,
                segmentOrigin,
                contradictionStatus
        );
    }

    public AssertionProvenance withSegmentOrigin(InputSegmentOrigin segmentOrigin) {
        return new AssertionProvenance(
                source,
                producedBy,
                cycleIndex,
                timestamp,
                mode,
                supportingAssertionIds,
                normalizedFromId,
                segmentOrigin,
                contradictionStatus
        );
    }

    public AssertionProvenance withContradictionStatus(ContradictionStatus contradictionStatus) {
        return new AssertionProvenance(
                source,
                producedBy,
                cycleIndex,
                timestamp,
                mode,
                supportingAssertionIds,
                normalizedFromId,
                segmentOrigin,
                contradictionStatus
        );
    }
}
