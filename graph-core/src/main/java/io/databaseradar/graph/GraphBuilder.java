package io.databaseradar.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphBuilder {
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<EdgeKey, MutableEdge> edges = new LinkedHashMap<>();

    public void addNode(GraphNode node) {
        GraphNode previous = nodes.putIfAbsent(node.id(), node);
        if (previous != null && previous.kind() != node.kind()) {
            throw new IllegalArgumentException("Node ID reused with another kind: " + node.id());
        }
    }

    public void addEdge(String from, EdgeKind kind, String to, Evidence evidence) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("Both edge endpoints must exist: " + from + " -> " + to);
        }
        EdgeKey key = new EdgeKey(from, kind, to);
        edges.computeIfAbsent(key, ignored -> new MutableEdge(evidence.confidence()))
                .add(evidence);
    }

    public EvidenceGraph build() {
        List<GraphNode> sortedNodes = nodes.values().stream()
                .sorted(Comparator.comparing(GraphNode::id))
                .toList();
        List<GraphEdge> sortedEdges = edges.entrySet().stream()
                .map(entry -> entry.getValue().freeze(entry.getKey()))
                .sorted(Comparator.comparing(GraphEdge::from)
                        .thenComparing(edge -> edge.edge().name())
                        .thenComparing(GraphEdge::to))
                .toList();
        return new EvidenceGraph(sortedNodes, sortedEdges);
    }

    private record EdgeKey(String from, EdgeKind kind, String to) {
    }

    private static final class MutableEdge {
        private Confidence confidence;
        private final List<Evidence> evidence = new ArrayList<>();

        private MutableEdge(Confidence confidence) {
            this.confidence = confidence;
        }

        private void add(Evidence item) {
            confidence = Confidence.max(confidence, item.confidence());
            if (!evidence.contains(item)) {
                evidence.add(item);
            }
        }

        private GraphEdge freeze(EdgeKey key) {
            List<Evidence> sorted = evidence.stream()
                    .sorted(Comparator.comparing(Evidence::file)
                            .thenComparingInt(item -> item.range() == null ? Integer.MAX_VALUE : item.range().start().line())
                            .thenComparing(Evidence::kind))
                    .toList();
            return new GraphEdge(key.from(), key.kind(), key.to(), confidence, sorted);
        }
    }
}
