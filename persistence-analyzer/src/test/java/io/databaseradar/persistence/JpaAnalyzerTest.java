package io.databaseradar.persistence;

import com.github.javaparser.StaticJavaParser;
import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.GraphBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaAnalyzerTest {
    @Test
    void mapsJavaxEntityWithoutLoadingPersistenceApi() {
        var unit = StaticJavaParser.parse("""
                package com.acme;
                import javax.persistence.*;
                @Entity
                @Table(name = "PEDIDO", schema = "ERP")
                class Pedido {
                    @Column(name = "STATUS") String status;
                    String descricao;
                    @Transient String calculated;
                }
                """);
        GraphBuilder graph = new GraphBuilder();

        new JpaAnalyzer().analyze(unit, "src/Pedido.java", graph);

        var result = graph.build();
        assertTrue(result.node("table:ERP.PEDIDO").isPresent());
        assertTrue(result.node("column:ERP.PEDIDO.STATUS").isPresent());
        assertTrue(result.node("column:ERP.PEDIDO.DESCRICAO").isPresent());
        assertTrue(result.node("column:ERP.PEDIDO.CALCULATED").isEmpty());
        assertEquals(Confidence.HIGH, result.edges().stream()
                .filter(edge -> edge.edge() == EdgeKind.MAPS_TO_COLUMN)
                .filter(edge -> edge.to().endsWith(".STATUS"))
                .findFirst().orElseThrow().confidence());
        assertEquals(Confidence.MEDIUM, result.edges().stream()
                .filter(edge -> edge.edge() == EdgeKind.MAPS_TO_COLUMN)
                .filter(edge -> edge.to().endsWith(".DESCRICAO"))
                .findFirst().orElseThrow().confidence());
    }
}
