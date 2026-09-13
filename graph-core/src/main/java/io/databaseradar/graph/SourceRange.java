package io.databaseradar.graph;

import java.util.Objects;

public record SourceRange(SourcePosition start, SourcePosition end) {
    public SourceRange {
        Objects.requireNonNull(start, "start");
    }
}
