package io.databaseradar.report;

import io.databaseradar.graph.GraphDocument;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.graph.ScanSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphJsonTest {
    @TempDir
    Path directory;

    @Test
    void writesStableSchemaAndReadsItBack() throws Exception {
        ScanSummary summary = new ScanSummary(1, 1, 0, 0, 0, 1, 1, 0,
                1, 0, 1, 0, 0, 0, 10, 4, 2, 1024);
        GraphDocument document = new GraphDocument("1.0", "test", "/repo", "fixed",
                summary,
                List.of(new GraphNode("table:PEDIDO", NodeKind.DATABASE_TABLE, "PEDIDO", Map.of())),
                List.of(), List.of());
        Path output = directory.resolve("graph.json");

        GraphJson json = new GraphJson();
        json.write(document, output);

        String serialized = java.nio.file.Files.readString(output);
        assertTrue(serialized.contains("\"schemaVersion\" : \"1.0\""));
        assertEquals(document, json.read(output));
    }
}
