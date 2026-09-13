package io.databaseradar.java;

import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.NodeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScannerTest {
    @TempDir
    Path root;

    @Test
    void scansBrokenClasspathInnerClassesOverloadsJdbcAndCallPath() throws IOException {
        write("src/main/java/com/acme/PedidoDAO.java", """
                package com.acme;
                import missing.vendor.BaseDao;
                class PedidoDAO extends BaseDao {
                    static final String TABLE = "PED" + "IDO";
                    void buscar(String status) throws Exception {
                        connection.prepareStatement(
                            "SELECT p.ID, p.STATUS FROM " + TABLE + " p WHERE p.STATUS = ?");
                    }
                    void buscar(int id) { }
                    class Interna { void apagar() { executeUpdate("DELETE FROM PEDIDO WHERE ID = ?"); } }
                }
                """);
        write("src/main/java/com/acme/PedidoService.java", """
                package com.acme;
                class PedidoService {
                    PedidoDAO dao;
                    void processar() throws Exception { dao.buscar("PENDENTE"); }
                }
                """);
        write("src/main/java/com/acme/DynamicDao.java", """
                package com.acme;
                class DynamicDao {
                    void buscar(String tableName) { executeQuery("SELECT * FROM " + tableName); }
                }
                """);
        write("src/main/java/com/acme/Broken.java", "class Broken { void x( }");
        write("src/main/resources/insert.sql", "INSERT INTO pedido (id, status) VALUES (?, ?);");

        ScanResult result = new ProjectScanner().scan(ScanOptions.defaults(root));

        assertEquals(4, result.summary().javaFiles());
        assertEquals(3, result.summary().parsedJavaFiles());
        assertEquals(1, result.summary().javaParseFailures());
        assertTrue(result.graph().node("table:PEDIDO").isPresent());
        assertTrue(result.graph().node("column:PEDIDO.STATUS").isPresent());
        assertEquals(2, result.graph().nodes().stream()
                .filter(node -> node.kind() == NodeKind.JAVA_METHOD)
                .filter(node -> node.id().contains("PedidoDAO#buscar"))
                .count());
        assertTrue(result.graph().edges().stream().anyMatch(edge ->
                (edge.edge() == EdgeKind.CALLS || edge.edge() == EdgeKind.POSSIBLY_CALLS)
                        && edge.from().contains("PedidoService#processar")
                        && edge.to().contains("PedidoDAO#buscar(String)")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.category().equals("DYNAMIC_SQL_UNKNOWN")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.category().equals("JAVA_PARSE_FAILURE")));
    }

    private void write(String relative, String contents) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
