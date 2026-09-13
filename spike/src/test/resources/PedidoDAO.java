package com.acme.pedido;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class PedidoDAO {
    public PreparedStatement buscar(Connection connection) throws Exception {
        return connection.prepareStatement(
                "SELECT ID, STATUS FROM PEDIDO WHERE STATUS = ?");
    }
}
