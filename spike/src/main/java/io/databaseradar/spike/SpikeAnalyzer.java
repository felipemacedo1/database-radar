package io.databaseradar.spike;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.io.IOException;
import java.nio.file.Path;

public final class SpikeAnalyzer {
    public SpikeFinding analyze(Path source) throws Exception {
        CompilationUnit unit = parseJava8(source);
        MethodCallExpr jdbcCall = unit.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("prepareStatement"))
                .filter(call -> call.getArguments().size() == 1)
                .filter(call -> call.getArgument(0).isStringLiteralExpr())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No JDBC SQL literal found"));

        StringLiteralExpr literal = jdbcCall.getArgument(0).asStringLiteralExpr();
        MethodDeclaration method = jdbcCall.findAncestor(MethodDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("SQL is not inside a method"));
        Statement statement = CCJSqlParserUtil.parse(literal.asString());
        if (!(statement instanceof Select select)
                || !(select.getPlainSelect() instanceof PlainSelect plainSelect)
                || !(plainSelect.getFromItem() instanceof Table table)) {
            throw new IllegalArgumentException("Spike expects SELECT ... FROM <table>");
        }

        return new SpikeFinding(
                method.getNameAsString(),
                "READS_TABLE",
                table.getFullyQualifiedName().toUpperCase(),
                source.toString(),
                literal.getBegin().orElseThrow().line,
                "HIGH");
    }

    private CompilationUnit parseJava8(Path source) throws IOException {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);
        ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(source);
        return result.getResult().orElseThrow(() ->
                new IllegalArgumentException("Java parse failed: " + result.getProblems()));
    }
}
