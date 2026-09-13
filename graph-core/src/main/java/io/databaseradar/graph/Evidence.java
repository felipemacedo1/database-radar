package io.databaseradar.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record Evidence(
        String file,
        SourceRange range,
        String analyzer,
        String kind,
        Confidence confidence,
        Map<String, String> details) {

    public Evidence {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(analyzer, "analyzer");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(confidence, "confidence");
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(details));
    }
}
