package io.databaseradar.java;

import io.databaseradar.graph.Diagnostic;
import io.databaseradar.graph.EvidenceGraph;
import io.databaseradar.graph.ScanSummary;

import java.util.List;

public record ScanResult(
        String scannedRoot,
        EvidenceGraph graph,
        List<Diagnostic> diagnostics,
        ScanSummary summary) {

    public ScanResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
