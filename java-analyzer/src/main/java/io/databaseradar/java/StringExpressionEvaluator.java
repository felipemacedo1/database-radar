package io.databaseradar.java;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.databaseradar.graph.Confidence;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class StringExpressionEvaluator {
    static final String DYNAMIC_MARKER = "__DBR_DYNAMIC__";

    Evaluation evaluate(Expression expression, Map<String, Binding> bindings) {
        return evaluate(expression, bindings, new HashSet<>(), 0);
    }

    private Evaluation evaluate(
            Expression expression,
            Map<String, Binding> bindings,
            Set<String> visiting,
            int depth) {
        if (depth > 32) {
            return Evaluation.dynamic(DYNAMIC_MARKER);
        }
        if (expression instanceof StringLiteralExpr literal) {
            return Evaluation.resolved(literal.asString(), Confidence.HIGH);
        }
        if (expression instanceof EnclosedExpr enclosed) {
            return evaluate(enclosed.getInner(), bindings, visiting, depth + 1);
        }
        if (expression instanceof NameExpr name) {
            Binding binding = bindings.get(name.getNameAsString());
            if (binding == null || !visiting.add(name.getNameAsString())) {
                return Evaluation.dynamic(DYNAMIC_MARKER);
            }
            Evaluation value = evaluate(binding.expression(), bindings, visiting, depth + 1);
            visiting.remove(name.getNameAsString());
            if (value.resolved() && !binding.stable()) {
                return new Evaluation(true, value.text(), Confidence.MEDIUM);
            }
            return value;
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Evaluation left = evaluate(binary.getLeft(), bindings, visiting, depth + 1);
            Evaluation right = evaluate(binary.getRight(), bindings, visiting, depth + 1);
            if (left.resolved() && right.resolved()) {
                return Evaluation.resolved(left.text() + right.text(),
                        Confidence.min(left.confidence(), right.confidence()));
            }
            return Evaluation.dynamic(left.text() + right.text());
        }
        return Evaluation.dynamic(DYNAMIC_MARKER);
    }

    record Binding(Expression expression, boolean stable) {
    }

    record Evaluation(boolean resolved, String text, Confidence confidence) {
        static Evaluation resolved(String text, Confidence confidence) {
            return new Evaluation(true, text, confidence);
        }

        static Evaluation dynamic(String text) {
            return new Evaluation(false, text, Confidence.UNKNOWN);
        }
    }
}
