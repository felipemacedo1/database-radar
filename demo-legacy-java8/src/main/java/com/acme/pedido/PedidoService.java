package com.acme.pedido;

import java.sql.Connection;
import java.sql.SQLException;

public class PedidoService {
    private final PedidoDAO dao;

    public PedidoService(PedidoDAO dao) {
        this.dao = dao;
    }

    public void processar(Connection connection) throws SQLException {
        dao.buscarPendentes(connection);
        dao.atualizarStatus(connection);
    }
}
