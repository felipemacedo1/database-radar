package com.acme.pedido;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class PedidoDAO {
    private static final String TABLE = "PED" + "IDO";

    public PreparedStatement buscarPendentes(Connection connection) throws SQLException {
        String sql = "SELECT p.ID, p.STATUS, c.NOME "
                + "FROM " + TABLE + " p "
                + "JOIN CLIENTE c ON c.ID = p.CLIENTE_ID "
                + "WHERE p.STATUS = ?";
        return connection.prepareStatement(sql);
    }

    public PreparedStatement inserir(Connection connection) throws SQLException {
        return connection.prepareStatement(
                "INSERT INTO PEDIDO (ID, STATUS, CLIENTE_ID) VALUES (?, ?, ?)");
    }

    public PreparedStatement atualizarStatus(Connection connection) throws SQLException {
        return connection.prepareStatement(
                "UPDATE PEDIDO SET STATUS = ? WHERE ID = ?");
    }

    public int excluir(Statement statement) throws SQLException {
        return statement.executeUpdate("DELETE FROM PEDIDO WHERE ID = 42");
    }

    public PreparedStatement consultarTabelaDinamica(Connection connection, String tableName)
            throws SQLException {
        return connection.prepareStatement("SELECT * FROM " + tableName);
    }

    public InputStream carregarConsultaArquivada() {
        return PedidoDAO.class.getResourceAsStream("/sql/pedidos-arquivados.sql");
    }
}
