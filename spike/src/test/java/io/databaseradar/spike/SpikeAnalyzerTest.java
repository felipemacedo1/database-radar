package io.databaseradar.spike;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpikeAnalyzerTest {
    @Test
    void linksJava8JdbcSelectToMethodTableAndSourceLine() throws Exception {
        Path fixture = Path.of("src/test/resources/PedidoDAO.java");

        SpikeFinding finding = new SpikeAnalyzer().analyze(fixture);

        assertEquals("buscar", finding.method());
        assertEquals("READS_TABLE", finding.edge());
        assertEquals("PEDIDO", finding.table());
        assertEquals(9, finding.line());
        assertEquals("HIGH", finding.confidence());
    }
}
