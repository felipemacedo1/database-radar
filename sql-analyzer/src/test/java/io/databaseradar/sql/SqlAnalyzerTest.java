package io.databaseradar.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalyzerTest {
    private final SqlAnalyzer analyzer = new SqlAnalyzer();

    @ParameterizedTest
    @MethodSource("writes")
    void classifiesWritesAndTargetColumns(String sql, SqlOperation operation, String table, String column) {
        SqlAnalysis result = analyzer.analyze(sql);

        assertTrue(result.parsed(), result.error());
        assertEquals(operation, result.operation());
        assertTrue(result.tables().contains(new SqlTableAccess(table, AccessMode.WRITE)));
        if (column != null) {
            assertTrue(result.columns().contains(new SqlColumnAccess(table, column, AccessMode.WRITE)));
        }
    }

    static Stream<Arguments> writes() {
        return Stream.of(
                Arguments.of("INSERT INTO pedido (id, status) VALUES (?, ?)", SqlOperation.INSERT, "PEDIDO", "STATUS"),
                Arguments.of("UPDATE public.pedido SET status = ? WHERE id = ?", SqlOperation.UPDATE, "PUBLIC.PEDIDO", "STATUS"),
                Arguments.of("DELETE FROM pedido WHERE id = ?", SqlOperation.DELETE, "PEDIDO", null));
    }

    @Test
    void resolvesAliasesAndJoinColumns() {
        SqlAnalysis result = analyzer.analyze("""
                SELECT p.id, c.nome
                FROM pedido p
                JOIN cliente c ON c.id = p.cliente_id
                WHERE p.status = ?
                """);

        assertTrue(result.parsed(), result.error());
        assertTrue(result.tables().contains(new SqlTableAccess("PEDIDO", AccessMode.READ)));
        assertTrue(result.tables().contains(new SqlTableAccess("CLIENTE", AccessMode.READ)));
        assertTrue(result.columns().contains(new SqlColumnAccess("PEDIDO", "STATUS", AccessMode.READ)));
        assertTrue(result.columns().contains(new SqlColumnAccess("CLIENTE", "NOME", AccessMode.READ)));
    }

    @Test
    void leavesUnqualifiedColumnAmbiguousAcrossJoin() {
        SqlAnalysis result = analyzer.analyze("SELECT id FROM pedido p JOIN cliente c ON c.id = p.cliente_id");

        assertTrue(result.parsed(), result.error());
        assertTrue(result.ambiguousColumns().contains("id"));
    }

    @Test
    void handlesQuotedIdentifiersWithoutInventingCase() {
        SqlAnalysis result = analyzer.analyze("SELECT p.\"Status\" FROM \"Pedido\" p");

        assertTrue(result.parsed(), result.error());
        assertTrue(result.columns().contains(new SqlColumnAccess("Pedido", "Status", AccessMode.READ)));
    }

    @Test
    void parsesSqlServerBracketsTopAndNamedVariable() {
        SqlAnalysis result = new SqlAnalyzer(SqlDialect.SQL_SERVER).analyze("""
                SELECT TOP (10) p.[ID], p.[STATUS]
                FROM [dbo].[PEDIDO] p
                WHERE p.[STATUS] = @status
                """);

        assertTrue(result.parsed(), result.error());
        assertTrue(result.tables().contains(new SqlTableAccess("dbo.PEDIDO", AccessMode.READ)));
        assertTrue(result.columns().contains(new SqlColumnAccess("dbo.PEDIDO", "STATUS", AccessMode.READ)));
    }

    @Test
    void resolvesSqlServerUpdateFromAliasToPhysicalTarget() {
        SqlAnalysis result = new SqlAnalyzer(SqlDialect.SQL_SERVER).analyze("""
                UPDATE p
                SET p.[STATUS] = ?
                FROM [dbo].[PEDIDO] p
                JOIN [dbo].[CLIENTE] c ON c.[ID] = p.[CLIENTE_ID]
                WHERE c.[ATIVO] = 1
                """);

        assertTrue(result.parsed(), result.error());
        assertTrue(result.tables().contains(new SqlTableAccess("dbo.PEDIDO", AccessMode.WRITE)));
        assertTrue(result.columns().contains(new SqlColumnAccess("dbo.PEDIDO", "STATUS", AccessMode.WRITE)));
        assertFalse(result.columns().contains(new SqlColumnAccess("dbo.PEDIDO", "STATUS", AccessMode.READ)));
        assertTrue(result.tables().contains(new SqlTableAccess("dbo.CLIENTE", AccessMode.READ)));
    }

    @Test
    void readsUpdateRightHandSideButNotAssignmentTarget() {
        SqlAnalysis result = analyzer.analyze(
                "UPDATE pedido SET status = previous_status WHERE id = ?");

        assertTrue(result.parsed(), result.error());
        assertTrue(result.columns().contains(new SqlColumnAccess("PEDIDO", "STATUS", AccessMode.WRITE)));
        assertFalse(result.columns().contains(new SqlColumnAccess("PEDIDO", "STATUS", AccessMode.READ)));
        assertTrue(result.columns().contains(new SqlColumnAccess("PEDIDO", "PREVIOUS_STATUS", AccessMode.READ)));
    }

    @Test
    void reportsInvalidSql() {
        SqlAnalysis result = analyzer.analyze("SELECT FROM WHERE");

        assertFalse(result.parsed());
        assertEquals(SqlOperation.UNSUPPORTED, result.operation());
    }
}
