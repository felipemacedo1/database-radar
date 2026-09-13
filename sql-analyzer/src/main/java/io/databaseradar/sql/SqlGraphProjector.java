package io.databaseradar.sql;

import io.databaseradar.graph.CanonicalIds;
import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.Diagnostic;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.GraphBuilder;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;

import java.util.List;
import java.util.Map;

public final class SqlGraphProjector {
    private final SqlAnalyzer analyzer;

    public SqlGraphProjector() {
        this(new SqlAnalyzer());
    }

    public SqlGraphProjector(SqlAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public ProjectionResult project(String sql, SqlOrigin origin, GraphBuilder graph, List<Diagnostic> diagnostics) {
        SqlAnalysis analysis = analyzer.analyze(sql);
        if (!analysis.parsed() || analysis.operation() == SqlOperation.UNSUPPORTED) {
            diagnostics.add(new Diagnostic(
                    "SQL_PARSE_FAILURE",
                    origin.file(),
                    origin.range(),
                    analysis.error() == null ? "Unsupported SQL statement" : analysis.error(),
                    Confidence.UNKNOWN));
            return new ProjectionResult(false, false);
        }

        String sqlId = CanonicalIds.sql(sql);
        graph.addNode(new GraphNode(sqlId, NodeKind.SQL_STATEMENT, summary(sql),
                Map.of("operation", analysis.operation().name())));
        Evidence containsEvidence = evidence(origin, analysis.operation(), origin.confidence());
        graph.addEdge(origin.ownerNodeId(), EdgeKind.CONTAINS_SQL, sqlId, containsEvidence);

        for (SqlTableAccess access : analysis.tables()) {
            String tableId = CanonicalIds.table(access.table());
            graph.addNode(new GraphNode(tableId, NodeKind.DATABASE_TABLE, access.table(), Map.of()));
            graph.addEdge(sqlId,
                    access.mode() == AccessMode.READ ? EdgeKind.READS_TABLE : EdgeKind.WRITES_TABLE,
                    tableId,
                    evidence(origin, analysis.operation(), origin.confidence()));
        }
        for (SqlColumnAccess access : analysis.columns()) {
            String tableId = CanonicalIds.table(access.table());
            String columnId = CanonicalIds.column(access.table(), access.column());
            graph.addNode(new GraphNode(tableId, NodeKind.DATABASE_TABLE, access.table(), Map.of()));
            graph.addNode(new GraphNode(columnId, NodeKind.DATABASE_COLUMN,
                    access.table() + "." + access.column(), Map.of("table", tableId)));
            graph.addEdge(sqlId,
                    access.mode() == AccessMode.READ ? EdgeKind.READS_COLUMN : EdgeKind.WRITES_COLUMN,
                    columnId,
                    evidence(origin, analysis.operation(), origin.confidence()));
        }
        for (String column : analysis.ambiguousColumns()) {
            diagnostics.add(new Diagnostic(
                    "AMBIGUOUS_SQL_COLUMN",
                    origin.file(),
                    origin.range(),
                    "Unable to assign column to one table: " + column,
                    Confidence.LOW));
        }
        return new ProjectionResult(true, !analysis.ambiguousColumns().isEmpty());
    }

    private static Evidence evidence(SqlOrigin origin, SqlOperation operation, Confidence confidence) {
        return new Evidence(
                origin.file(),
                origin.range(),
                "sql-ast",
                origin.evidenceKind(),
                confidence,
                Map.of("operation", operation.name()));
    }

    private static String summary(String sql) {
        String oneLine = sql.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 117) + "...";
    }

    public record ProjectionResult(boolean parsed, boolean partial) {
    }
}
