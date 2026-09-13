package com.acme.pedido;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "PEDIDO")
public class Pedido {
    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CLIENTE_ID")
    private Long clienteId;
}
