package io.databaseradar.report;

import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.GraphDocument;
import io.databaseradar.graph.GraphNode;

import java.util.List;

public final class HumanReport {
    public String scanSummary(GraphDocument document, String output) {
        var summary = document.summary();
        return """
                Scan complete
                Java files: %d
                Parsed: %d
                Parse failures: %d
                Resolved calls: %d
                Unresolved calls: %d
                SQL candidates: %d
                SQL parsed: %d
                SQL partial/ambiguous: %d
                Database tables: %d
                Database columns: %d
                High confidence edges: %d
                Medium confidence edges: %d
                Low confidence edges: %d
                Unknown findings: %d
                Duration: %d ms
                Approximate peak heap: %s
                Graph written to: %s
                """.formatted(
                summary.javaFiles(), summary.parsedJavaFiles(), summary.javaParseFailures(),
                summary.resolvedCalls(), summary.unresolvedCalls(), summary.sqlCandidates(),
                summary.parsedSql(), summary.partialSql(), summary.databaseTables(),
                summary.databaseColumns(), summary.highConfidenceEdges(),
                summary.mediumConfidenceEdges(), summary.lowConfidenceEdges(),
                summary.unknownFindings(), summary.durationMillis(), bytes(summary.approximatePeakHeapBytes()), output);
    }

    public String accesses(String heading, List<GraphQueries.AccessFinding> findings) {
        if (findings.isEmpty()) {
            return heading + "\n  none\n";
        }
        StringBuilder output = new StringBuilder(heading).append('\n');
        for (GraphQueries.AccessFinding finding : findings) {
            output.append("  ").append(finding.originLabel()).append('\n');
            appendEvidence(output, finding.evidence());
            output.append("    relation: ").append(finding.access()).append('\n');
            output.append("    confidence: ").append(finding.confidence()).append('\n');
        }
        return output.toString();
    }

    public String impact(String entity, List<GraphQueries.ImpactPath> paths, GraphQueries queries) {
        StringBuilder output = new StringBuilder("ENTITY: ").append(entity).append('\n');
        if (paths.isEmpty()) {
            return output.append("\nNo read/write paths found.\n").toString();
        }
        output.append("\nIMPACT PATHS\n");
        for (GraphQueries.ImpactPath path : paths) {
            output.append("  ");
            for (int index = 0; index < path.nodes().size(); index++) {
                if (index > 0) {
                    output.append(" -> ");
                }
                output.append(queries.label(path.nodes().get(index)));
            }
            output.append('\n').append("    confidence: ").append(path.confidence()).append('\n');
        }
        return output.toString();
    }

    public String nodes(List<GraphNode> nodes) {
        if (nodes.isEmpty()) {
            return "none\n";
        }
        StringBuilder output = new StringBuilder();
        nodes.forEach(node -> output.append(node.label()).append("  [").append(node.id()).append("]\n"));
        return output.toString();
    }

    private static void appendEvidence(StringBuilder output, Evidence evidence) {
        if (evidence == null) {
            return;
        }
        output.append("    evidence: ").append(evidence.file());
        if (evidence.range() != null) {
            output.append(':').append(evidence.range().start().line());
        }
        output.append('\n').append("    via: ").append(evidence.kind()).append('\n');
    }

    private static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return "%.1f KiB".formatted(value / 1024.0);
        }
        return "%.1f MiB".formatted(value / (1024.0 * 1024.0));
    }
}
