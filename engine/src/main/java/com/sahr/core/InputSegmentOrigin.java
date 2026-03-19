package com.sahr.core;

public record InputSegmentOrigin(int index, int total, String text) {
    public InputSegmentOrigin {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (total <= 0) {
            throw new IllegalArgumentException("total must be positive");
        }
        text = text == null ? "" : text;
    }

    public String label() {
        return "s" + (index + 1) + "/" + total;
    }
}
