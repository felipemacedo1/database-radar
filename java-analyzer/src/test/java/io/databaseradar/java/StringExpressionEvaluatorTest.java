package io.databaseradar.java;

import com.github.javaparser.StaticJavaParser;
import io.databaseradar.graph.Confidence;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringExpressionEvaluatorTest {
    private final StringExpressionEvaluator evaluator = new StringExpressionEvaluator();

    @Test
    void resolvesLiteralConcatenationAndConstant() {
        var binding = new StringExpressionEvaluator.Binding(
                StaticJavaParser.parseExpression("\"PED\" + \"IDO\""), true);

        var result = evaluator.evaluate(
                StaticJavaParser.parseExpression("\"SELECT * FROM \" + TABLE"),
                Map.of("TABLE", binding));

        assertTrue(result.resolved());
        assertEquals("SELECT * FROM PEDIDO", result.text());
        assertEquals(Confidence.HIGH, result.confidence());
    }

    @Test
    void preservesUnknownRuntimeHole() {
        var result = evaluator.evaluate(
                StaticJavaParser.parseExpression("\"SELECT * FROM \" + tableName"), Map.of());

        assertFalse(result.resolved());
        assertTrue(result.text().contains(StringExpressionEvaluator.DYNAMIC_MARKER));
        assertEquals(Confidence.UNKNOWN, result.confidence());
    }
}
