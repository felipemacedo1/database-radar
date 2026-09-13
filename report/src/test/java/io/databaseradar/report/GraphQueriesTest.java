package io.databaseradar.report;

import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.GraphBuilder;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.graph.SourcePosition;
import io.databaseradar.graph.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQueriesTest {
    @Test
    void resolvesSqlServerBracketedTargetWhenParserCanonicalizedItsCase() {
        GraphBuilder builder = new GraphBuilder();
        node(builder, "column:DBO.PEDIDO.STATUS", NodeKind.DATABASE_COLUMN);

        var target = new GraphQueries(builder.build())
                .databaseTarget("column", "[dbo].[PEDIDO].[STATUS]");

        assertTrue(target.isPresent());
        assertEquals("column:DBO.PEDIDO.STATUS", target.orElseThrow().id());
    }

    @Test
    void impactTraversesIncomingCallsStopsCyclesAndUsesWeakestConfidence() {
        GraphBuilder builder = new GraphBuilder();
        node(builder, "method:service", NodeKind.JAVA_METHOD);
        node(builder, "method:dao", NodeKind.JAVA_METHOD);
        node(builder, "sql:q", NodeKind.SQL_STATEMENT);
        node(builder, "column:PEDIDO.STATUS", NodeKind.DATABASE_COLUMN);
        edge(builder, "method:service", EdgeKind.POSSIBLY_CALLS, "method:dao", Confidence.MEDIUM);
        edge(builder, "method:dao", EdgeKind.CALLS, "method:service", Confidence.HIGH);
        edge(builder, "method:dao", EdgeKind.CONTAINS_SQL, "sql:q", Confidence.HIGH);
        edge(builder, "sql:q", EdgeKind.READS_COLUMN, "column:PEDIDO.STATUS", Confidence.HIGH);

        var paths = new GraphQueries(builder.build()).impact("column:PEDIDO.STATUS", 5);

        assertEquals(2, paths.size());
        assertTrue(paths.stream().anyMatch(path -> path.nodes().getFirst().equals("method:service")
                && path.confidence() == Confidence.MEDIUM));
    }

    private static void node(GraphBuilder builder, String id, NodeKind kind) {
        builder.addNode(new GraphNode(id, kind, id, Map.of()));
    }

    private static void edge(GraphBuilder builder, String from, EdgeKind kind, String to, Confidence confidence) {
        SourcePosition position = new SourcePosition(1, 1);
        builder.addEdge(from, kind, to, new Evidence("Test.java", new SourceRange(position, position),
                "test", "TEST", confidence, Map.of()));
    }
}
