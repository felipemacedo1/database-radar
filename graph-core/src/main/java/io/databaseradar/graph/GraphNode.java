package io.databaseradar.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record GraphNode(String id, NodeKind kind, String label, Map<String, String> attributes) {
    public GraphNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(attributes));
    }
}
