package io.databaseradar.persistence;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import io.databaseradar.graph.CanonicalIds;
import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.GraphBuilder;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.graph.SourcePosition;
import io.databaseradar.graph.SourceRange;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

public final class JpaAnalyzer {
    public void analyze(CompilationUnit unit, String file, GraphBuilder graph) {
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            Optional<AnnotationExpr> entityAnnotation = annotation(type, "Entity");
            if (entityAnnotation.isEmpty()) {
                continue;
            }
            String qualifiedType = qualifiedName(unit, type);
            String fileId = CanonicalIds.file(file);
            String entityId = CanonicalIds.entity(qualifiedType);
            graph.addNode(new GraphNode(fileId, NodeKind.SOURCE_FILE, file, Map.of()));
            graph.addNode(new GraphNode(entityId, NodeKind.ENTITY, qualifiedType, Map.of("javaType", qualifiedType)));
            graph.addEdge(fileId, EdgeKind.DECLARES, entityId,
                    evidence(file, entityAnnotation.get(), "JPA_ENTITY", Confidence.HIGH, Map.of()));

            Optional<AnnotationExpr> tableAnnotation = annotation(type, "Table");
            String tableName = tableAnnotation.flatMap(annotation -> stringMember(annotation, "name"))
                    .orElse(type.getNameAsString());
            String schema = tableAnnotation.flatMap(annotation -> stringMember(annotation, "schema"))
                    .orElse("");
            String qualifiedTable = schema.isBlank() ? tableName : schema + "." + tableName;
            Confidence tableConfidence = tableAnnotation.flatMap(annotation -> stringMember(annotation, "name"))
                    .isPresent() ? Confidence.HIGH : Confidence.MEDIUM;
            String tableId = CanonicalIds.table(qualifiedTable);
            graph.addNode(new GraphNode(tableId, NodeKind.DATABASE_TABLE,
                    CanonicalIds.normalizeQualifiedName(qualifiedTable), Map.of()));
            graph.addEdge(entityId, EdgeKind.MAPS_TO_TABLE, tableId,
                    evidence(file, tableAnnotation.orElse(entityAnnotation.get()),
                            tableConfidence == Confidence.HIGH ? "JPA_TABLE_EXPLICIT" : "JPA_TABLE_DEFAULT",
                            tableConfidence,
                            Map.of("naming", tableConfidence == Confidence.HIGH ? "explicit" : "java-type-default")));

            for (FieldDeclaration field : type.getFields()) {
                if (field.isStatic() || annotation(field, "Transient").isPresent()) {
                    continue;
                }
                Optional<AnnotationExpr> columnAnnotation = annotation(field, "Column");
                for (VariableDeclarator variable : field.getVariables()) {
                    String columnName = columnAnnotation.flatMap(annotation -> stringMember(annotation, "name"))
                            .orElse(variable.getNameAsString());
                    Confidence columnConfidence = columnAnnotation.flatMap(annotation -> stringMember(annotation, "name"))
                            .isPresent() ? Confidence.HIGH : Confidence.MEDIUM;
                    String fieldId = CanonicalIds.field(qualifiedType, variable.getNameAsString());
                    String columnId = CanonicalIds.column(qualifiedTable, columnName);
                    graph.addNode(new GraphNode(fieldId, NodeKind.JAVA_FIELD,
                            qualifiedType + "." + variable.getNameAsString(),
                            Map.of("type", variable.getTypeAsString())));
                    graph.addNode(new GraphNode(columnId, NodeKind.DATABASE_COLUMN,
                            CanonicalIds.normalizeQualifiedName(qualifiedTable) + "." + CanonicalIds.normalizePart(columnName),
                            Map.of("table", tableId)));
                    graph.addEdge(entityId, EdgeKind.DECLARES, fieldId,
                            evidence(file, variable, "JAVA_FIELD", Confidence.HIGH, Map.of()));
                    Node columnEvidence = columnAnnotation.<Node>map(annotation -> annotation).orElse(variable);
                    graph.addEdge(fieldId, EdgeKind.MAPS_TO_COLUMN, columnId,
                            evidence(file, columnEvidence,
                                    columnConfidence == Confidence.HIGH ? "JPA_COLUMN_EXPLICIT" : "JPA_COLUMN_DEFAULT",
                                    columnConfidence,
                                    Map.of("naming", columnConfidence == Confidence.HIGH ? "explicit" : "java-field-default")));
                }
            }
        }
    }

    private static Optional<AnnotationExpr> annotation(Node node, String simpleName) {
        if (!(node instanceof com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> annotated)) {
            return Optional.empty();
        }
        return annotated.getAnnotations().stream()
                .filter(item -> item.getNameAsString().equals(simpleName)
                        || item.getNameAsString().endsWith("." + simpleName))
                .findFirst();
    }

    private static Optional<String> stringMember(AnnotationExpr annotation, String member) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return Optional.empty();
        }
        return normal.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals(member))
                .map(pair -> pair.getValue())
                .filter(Expression::isStringLiteralExpr)
                .map(value -> value.asStringLiteralExpr().asString())
                .findFirst();
    }

    private static String qualifiedName(CompilationUnit unit, TypeDeclaration<?> type) {
        Deque<String> names = new ArrayDeque<>();
        Node current = type;
        while (current instanceof TypeDeclaration<?> currentType) {
            names.addFirst(currentType.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        String localName = String.join(".", names);
        return unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString() + "." + localName)
                .orElse(localName);
    }

    private static Evidence evidence(
            String file,
            Node node,
            String kind,
            Confidence confidence,
            Map<String, String> details) {
        return new Evidence(file, range(node), "jpa-syntax", kind, confidence, details);
    }

    private static SourceRange range(Node node) {
        Range range = node.getRange().orElse(new Range(new Position(1, 1), new Position(1, 1)));
        return new SourceRange(
                new SourcePosition(range.begin.line, range.begin.column),
                new SourcePosition(range.end.line, range.end.column));
    }
}
