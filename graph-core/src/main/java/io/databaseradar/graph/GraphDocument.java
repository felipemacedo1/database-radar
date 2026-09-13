package io.databaseradar.graph;

import java.util.List;

public record GraphDocument(
        String schemaVersion,
        String toolVersion,
        String scannedRoot,
        String createdAt,
        ScanSummary summary,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<Diagnostic> diagnostics) {

    public GraphDocument {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        diagnostics = List.copyOf(diagnostics);
    }

    public EvidenceGraph graph() {
        return new EvidenceGraph(nodes, edges);
    }
}
