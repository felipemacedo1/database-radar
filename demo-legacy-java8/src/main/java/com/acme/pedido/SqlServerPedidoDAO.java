package com.acme.pedido;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SqlServerPedidoDAO {
    public PreparedStatement buscarRecentes(Connection connection) throws SQLException {
        return connection.prepareStatement(
                "SELECT TOP (10) p.[ID], p.[STATUS] "
                        + "FROM [dbo].[PEDIDO] p "
                        + "WHERE p.[STATUS] = @status");
    }

    public PreparedStatement atualizarComCliente(Connection connection) throws SQLException {
        return connection.prepareStatement(
                "UPDATE p SET p.[STATUS] = ? "
                        + "FROM [dbo].[PEDIDO] p "
                        + "JOIN [dbo].[CLIENTE] c ON c.[ID] = p.[CLIENTE_ID] "
                        + "WHERE c.[ATIVO] = 1");
    }
}
