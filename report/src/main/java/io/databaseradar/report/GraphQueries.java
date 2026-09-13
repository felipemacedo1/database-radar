package io.databaseradar.report;

import io.databaseradar.graph.CanonicalIds;
import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.EvidenceGraph;
import io.databaseradar.graph.GraphEdge;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class GraphQueries {
    private final EvidenceGraph graph;

    public GraphQueries(EvidenceGraph graph) {
        this.graph = graph;
    }

    public Optional<GraphNode> databaseTarget(String kind, String value) {
        String id = switch (kind.toLowerCase()) {
            case "table" -> CanonicalIds.table(value);
            case "column" -> {
                int separator = value.lastIndexOf('.');
                if (separator < 1 || separator == value.length() - 1) {
                    throw new IllegalArgumentException("Column must be TABLE.COLUMN or SCHEMA.TABLE.COLUMN");
                }
                yield CanonicalIds.column(value.substring(0, separator), value.substring(separator + 1));
            }
            default -> throw new IllegalArgumentException("Target kind must be table or column");
        };
        Optional<GraphNode> exact = graph.node(id);
        if (exact.isPresent()) {
            return exact;
        }
        NodeKind expectedKind = kind.equalsIgnoreCase("table")
                ? NodeKind.DATABASE_TABLE
                : NodeKind.DATABASE_COLUMN;
        List<GraphNode> caseInsensitive = graph.nodes().stream()
                .filter(node -> node.kind() == expectedKind && node.id().equalsIgnoreCase(id))
                .toList();
        return caseInsensitive.size() == 1 ? Optional.of(caseInsensitive.getFirst()) : Optional.empty();
    }

    public List<AccessFinding> accesses(String targetId, boolean writes) {
        Set<EdgeKind> kinds = writes
                ? Set.of(EdgeKind.WRITES_TABLE, EdgeKind.WRITES_COLUMN)
                : Set.of(EdgeKind.READS_TABLE, EdgeKind.READS_COLUMN);
        List<AccessFinding> findings = new ArrayList<>();
        for (GraphEdge access : graph.incoming(targetId)) {
            if (!kinds.contains(access.edge())) {
                continue;
            }
            List<GraphEdge> owners = graph.incoming(access.from()).stream()
                    .filter(edge -> edge.edge() == EdgeKind.CONTAINS_SQL)
                    .toList();
            if (owners.isEmpty()) {
                findings.add(finding(access.from(), access, List.of(access.from(), targetId)));
            } else {
                for (GraphEdge owner : owners) {
                    findings.add(finding(owner.from(), access,
                            List.of(owner.from(), access.from(), targetId)));
                    GraphNode ownerNode = graph.node(owner.from()).orElse(null);
                    if (ownerNode != null && ownerNode.kind() == NodeKind.RESOURCE_FILE) {
                        graph.incoming(owner.from()).stream()
                                .filter(edge -> edge.edge() == EdgeKind.LOADS_RESOURCE)
                                .forEach(loader -> findings.add(finding(loader.from(), access,
                                        List.of(loader.from(), owner.from(), access.from(), targetId))));
                    }
                }
            }
        }
        return findings.stream()
                .distinct()
                .sorted(Comparator.comparing(AccessFinding::confidence).reversed()
                        .thenComparing(AccessFinding::originId))
                .toList();
    }

    public List<MappingFinding> mappings(String targetId) {
        return graph.incoming(targetId).stream()
                .filter(edge -> edge.edge() == EdgeKind.MAPS_TO_COLUMN || edge.edge() == EdgeKind.MAPS_TO_TABLE)
                .map(edge -> new MappingFinding(edge.from(), label(edge.from()), edge.confidence(), firstEvidence(edge)))
                .sorted(Comparator.comparing(MappingFinding::confidence).reversed()
                        .thenComparing(MappingFinding::originId))
                .toList();
    }

    public List<ImpactPath> impact(String targetId, int maxDepth) {
        List<ImpactPath> paths = new ArrayList<>();
        for (AccessFinding direct : combinedAccesses(targetId)) {
            paths.add(new ImpactPath(direct.path(), direct.confidence()));
            GraphNode origin = graph.node(direct.originId()).orElse(null);
            if (origin == null || origin.kind() != NodeKind.JAVA_METHOD) {
                continue;
            }
            ArrayDeque<Traversal> queue = new ArrayDeque<>();
            queue.add(new Traversal(origin.id(), direct.path(), direct.confidence(), 0));
            while (!queue.isEmpty()) {
                Traversal current = queue.removeFirst();
                if (current.depth() >= maxDepth) {
                    continue;
                }
                for (GraphEdge incoming : graph.incoming(current.nodeId())) {
                    if (incoming.edge() != EdgeKind.CALLS && incoming.edge() != EdgeKind.POSSIBLY_CALLS) {
                        continue;
                    }
                    if (current.path().contains(incoming.from())) {
                        continue;
                    }
                    List<String> extended = new ArrayList<>();
                    extended.add(incoming.from());
                    extended.addAll(current.path());
                    Confidence confidence = Confidence.min(current.confidence(), incoming.confidence());
                    if (incoming.edge() == EdgeKind.POSSIBLY_CALLS) {
                        confidence = Confidence.min(confidence, Confidence.MEDIUM);
                    }
                    List<String> immutable = List.copyOf(extended);
                    paths.add(new ImpactPath(immutable, confidence));
                    queue.addLast(new Traversal(incoming.from(), immutable, confidence, current.depth() + 1));
                }
            }
        }
        return paths.stream().distinct()
                .sorted(Comparator.comparing(ImpactPath::confidence).reversed()
                        .thenComparingInt(path -> path.nodes().size())
                        .thenComparing(path -> String.join("|", path.nodes())))
                .toList();
    }

    public Optional<List<String>> path(String from, String to, int maxDepth) {
        String source = resolveNodeId(from);
        String target = resolveNodeId(to);
        ArrayDeque<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(source));
        Set<String> visited = new HashSet<>();
        visited.add(source);
        while (!queue.isEmpty()) {
            List<String> path = queue.removeFirst();
            String tail = path.getLast();
            if (tail.equals(target)) {
                return Optional.of(path);
            }
            if (path.size() > maxDepth + 1) {
                continue;
            }
            for (GraphEdge edge : graph.outgoing(tail)) {
                if (visited.add(edge.to())) {
                    List<String> next = new ArrayList<>(path);
                    next.add(edge.to());
                    queue.addLast(List.copyOf(next));
                }
            }
        }
        return Optional.empty();
    }

    public String resolveNodeId(String value) {
        if (graph.node(value).isPresent()) {
            return value;
        }
        List<GraphNode> matches = graph.nodes().stream()
                .filter(node -> node.label().equals(value) || node.id().endsWith(value))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException(matches.isEmpty()
                    ? "Node not found: " + value
                    : "Node reference is ambiguous: " + value);
        }
        return matches.getFirst().id();
    }

    public String label(String nodeId) {
        return graph.node(nodeId).map(GraphNode::label).orElse(nodeId);
    }

    private List<AccessFinding> combinedAccesses(String targetId) {
        LinkedHashSet<AccessFinding> combined = new LinkedHashSet<>(accesses(targetId, true));
        combined.addAll(accesses(targetId, false));
        return List.copyOf(combined);
    }

    private AccessFinding finding(String originId, GraphEdge access, List<String> path) {
        Evidence evidence = firstEvidence(access);
        return new AccessFinding(originId, label(originId), access.edge(), access.confidence(), evidence, path);
    }

    private static Evidence firstEvidence(GraphEdge edge) {
        return edge.evidence().isEmpty() ? null : edge.evidence().getFirst();
    }

    public record AccessFinding(
            String originId,
            String originLabel,
            EdgeKind access,
            Confidence confidence,
            Evidence evidence,
            List<String> path) {
    }

    public record MappingFinding(
            String originId,
            String originLabel,
            Confidence confidence,
            Evidence evidence) {
    }

    public record ImpactPath(List<String> nodes, Confidence confidence) {
    }

    private record Traversal(String nodeId, List<String> path, Confidence confidence, int depth) {
    }
}
