package io.databaseradar.graph;

import java.util.Objects;

public record Diagnostic(
        String category,
        String file,
        SourceRange range,
        String message,
        Confidence confidence) {

    public Diagnostic {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(confidence, "confidence");
    }
}
