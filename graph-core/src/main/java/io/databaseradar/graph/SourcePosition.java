package io.databaseradar.graph;

public record SourcePosition(int line, int column) {
    public SourcePosition {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("Source positions are one-based");
        }
    }
}
