package io.databaseradar.graph;

import java.util.List;
import java.util.Optional;

public record EvidenceGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
    public EvidenceGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public Optional<GraphNode> node(String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }

    public List<GraphEdge> incoming(String nodeId) {
        return edges.stream().filter(edge -> edge.to().equals(nodeId)).toList();
    }

    public List<GraphEdge> outgoing(String nodeId) {
        return edges.stream().filter(edge -> edge.from().equals(nodeId)).toList();
    }
}
