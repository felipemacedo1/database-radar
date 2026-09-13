package io.databaseradar.graph;

import java.util.List;
import java.util.Objects;

public record GraphEdge(
        String from,
        EdgeKind edge,
        String to,
        Confidence confidence,
        List<Evidence> evidence) {

    public GraphEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(confidence, "confidence");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
