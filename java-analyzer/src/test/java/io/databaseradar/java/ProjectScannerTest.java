package io.databaseradar.java;

import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.sql.SqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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
                    void carregar() { getClass().getResourceAsStream("/insert.sql"); }
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
        assertTrue(result.graph().edges().stream().anyMatch(edge -> edge.edge() == EdgeKind.LOADS_RESOURCE));
        assertTrue(result.graph().edges().stream()
                .filter(edge -> edge.edge() == EdgeKind.READS_TABLE)
                .flatMap(edge -> edge.evidence().stream())
                .anyMatch(evidence -> evidence.file().endsWith("PedidoDAO.java")
                        && evidence.range().start().line() == 7));
    }

    @ParameterizedTest
    @MethodSource("sourceVersions")
    void parsesConfiguredJavaSourceVersions(int version, String source) throws IOException {
        write("version" + version + "/src/main/java/example/Example.java", source);

        ScanResult result = new ProjectScanner().scan(
                new ScanOptions(root.resolve("version" + version), version, java.util.List.of()));

        assertEquals(1, result.summary().javaFiles());
        assertEquals(1, result.summary().parsedJavaFiles());
        assertEquals(0, result.summary().javaParseFailures());
    }

    static Stream<Arguments> sourceVersions() {
        return Stream.of(
                Arguments.of(8, "package example; class Example { Runnable r = () -> {}; }"),
                Arguments.of(11, "package example; class Example { void x() { var value = 1; } }"),
                Arguments.of(17, "package example; record Example(String value) { }"),
                Arguments.of(21, """
                        package example;
                        class Example {
                            String value(Object input) {
                                return switch (input) {
                                    case String text -> text;
                                    default -> "";
                                };
                            }
                        }
                        """));
    }

    @Test
    void canDisableSymbolResolutionWhileRetainingSyntacticCalls() throws IOException {
        write("no-symbols/src/main/java/example/Example.java", """
                package example;
                class Example {
                    void first() { second(); }
                    void second() { }
                }
                """);

        ScanResult result = new ProjectScanner().scan(new ScanOptions(
                root.resolve("no-symbols"), 8, java.util.List.of(), SqlDialect.AUTO, false));

        assertEquals(1, result.summary().resolvedCalls());
        assertEquals(0, result.summary().symbolResolutionMillis());
        assertTrue(result.graph().edges().stream().anyMatch(edge -> edge.edge() == EdgeKind.CALLS));
    }

    private void write(String relative, String contents) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
