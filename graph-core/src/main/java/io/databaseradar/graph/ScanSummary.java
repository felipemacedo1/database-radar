package io.databaseradar.graph;

public record ScanSummary(
        int javaFiles,
        int parsedJavaFiles,
        int javaParseFailures,
        int resolvedCalls,
        int unresolvedCalls,
        int sqlCandidates,
        int parsedSql,
        int partialSql,
        int databaseTables,
        int databaseColumns,
        int highConfidenceEdges,
        int mediumConfidenceEdges,
        int lowConfidenceEdges,
        int unknownFindings,
        long durationMillis,
        long symbolResolutionMillis,
        long sqlParsingMillis,
        long approximatePeakHeapBytes) {
}
