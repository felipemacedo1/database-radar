package io.databaseradar.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphCoreTest {
    @Test
    void deduplicatesEdgesButRetainsDistinctEvidence() {
        GraphBuilder graph = new GraphBuilder();
        graph.addNode(new GraphNode("method:a", NodeKind.JAVA_METHOD, "a", Map.of()));
        graph.addNode(new GraphNode("table:T", NodeKind.DATABASE_TABLE, "T", Map.of()));
        Evidence first = evidence(10, Confidence.MEDIUM);
        Evidence second = evidence(11, Confidence.HIGH);

        graph.addEdge("method:a", EdgeKind.READS_TABLE, "table:T", first);
        graph.addEdge("method:a", EdgeKind.READS_TABLE, "table:T", second);

        GraphEdge edge = graph.build().edges().getFirst();
        assertEquals(Confidence.HIGH, edge.confidence());
        assertEquals(2, edge.evidence().size());
    }

    @Test
    void canonicalIdsDistinguishQuotedCaseAndOverloads() {
        assertEquals("table:PUBLIC.PEDIDO", CanonicalIds.table("public.pedido"));
        assertEquals("table:public.Pedido", CanonicalIds.table("\"public\".\"Pedido\""));
        assertEquals("method:a.T#m(String)", CanonicalIds.method("a.T", "m", List.of("String")));
        assertEquals("method:a.T#m(int)", CanonicalIds.method("a.T", "m", List.of("int")));
    }

    private Evidence evidence(int line, Confidence confidence) {
        SourcePosition position = new SourcePosition(line, 1);
        return new Evidence("A.java", new SourceRange(position, position), "test", "TEST", confidence, Map.of());
    }
}
