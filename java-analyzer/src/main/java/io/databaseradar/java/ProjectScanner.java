package io.databaseradar.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.databaseradar.graph.CanonicalIds;
import io.databaseradar.graph.Confidence;
import io.databaseradar.graph.Diagnostic;
import io.databaseradar.graph.EdgeKind;
import io.databaseradar.graph.Evidence;
import io.databaseradar.graph.EvidenceGraph;
import io.databaseradar.graph.GraphBuilder;
import io.databaseradar.graph.GraphEdge;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.graph.ScanSummary;
import io.databaseradar.graph.SourcePosition;
import io.databaseradar.graph.SourceRange;
import io.databaseradar.persistence.JpaAnalyzer;
import io.databaseradar.sql.SqlGraphProjector;
import io.databaseradar.sql.SqlAnalyzer;
import io.databaseradar.sql.SqlOrigin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProjectScanner {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", ".database-radar", "target", "build", "out", "node_modules");
    private static final Set<String> SQL_CALLS = Set.of(
            "prepareStatement", "prepareCall", "executeQuery", "executeUpdate", "execute", "addBatch");

    private final JpaAnalyzer jpaAnalyzer = new JpaAnalyzer();
    private final StringExpressionEvaluator stringEvaluator = new StringExpressionEvaluator();

    public ScanResult scan(ScanOptions options) throws IOException {
        Instant started = Instant.now();
        if (!Files.isDirectory(options.root())) {
            throw new IllegalArgumentException("Scan root is not a directory: " + options.root());
        }

        List<Path> files = discoverFiles(options);
        List<Path> javaFiles = files.stream().filter(path -> path.toString().endsWith(".java")).toList();
        List<Path> sqlFiles = files.stream().filter(path -> path.toString().endsWith(".sql")).toList();
        JavaParser parser = parser(options, javaFiles);
        SqlGraphProjector sqlProjector = new SqlGraphProjector(new SqlAnalyzer(options.sqlDialect()));
        GraphBuilder graph = new GraphBuilder();
        List<Diagnostic> diagnostics = new ArrayList<>();
        MutableMetrics metrics = new MutableMetrics(javaFiles.size());
        MethodIndex methods = new MethodIndex();
        List<PendingCall> pendingCalls = new ArrayList<>();
        List<PendingResourceLoad> pendingResourceLoads = new ArrayList<>();
        Set<String> projectedCandidates = new HashSet<>();

        for (Path javaFile : javaFiles) {
            String relative = displayPath(options.root(), javaFile);
            String fileId = CanonicalIds.file(relative);
            graph.addNode(new GraphNode(fileId, NodeKind.SOURCE_FILE, relative, Map.of()));
            try {
                ParseResult<CompilationUnit> parse = parser.parse(javaFile);
                if (parse.getResult().isEmpty()) {
                    metrics.javaParseFailures++;
                    diagnostics.add(new Diagnostic("JAVA_PARSE_FAILURE", relative, null,
                            bounded(parse.getProblems().toString()), Confidence.UNKNOWN));
                    continue;
                }
                CompilationUnit unit = parse.getResult().orElseThrow();
                if (!parse.getProblems().isEmpty()) {
                    metrics.javaParseFailures++;
                    diagnostics.add(new Diagnostic("JAVA_PARSE_FAILURE", relative, null,
                            "Partial AST retained; parser reported: " + bounded(parse.getProblems().toString()),
                            Confidence.UNKNOWN));
                } else {
                    metrics.parsedJavaFiles++;
                }
                analyzeCompilationUnit(unit, relative, fileId, graph, diagnostics, metrics,
                        methods, pendingCalls, pendingResourceLoads, projectedCandidates, sqlProjector,
                        options.symbolResolution());
            } catch (RuntimeException exception) {
                metrics.javaParseFailures++;
                diagnostics.add(new Diagnostic("JAVA_ANALYSIS_FAILURE", relative, null,
                        bounded(exception.toString()), Confidence.UNKNOWN));
            }
            metrics.sampleHeap();
        }

        linkCalls(graph, diagnostics, metrics, methods, pendingCalls);
        linkResources(options.root(), sqlFiles, graph, diagnostics, pendingResourceLoads);
        analyzeSqlResources(options.root(), sqlFiles, graph, diagnostics, metrics, sqlProjector);
        EvidenceGraph built = graph.build();
        metrics.finish(built, diagnostics, Duration.between(started, Instant.now()).toMillis());
        diagnostics.sort(Comparator.comparing(Diagnostic::file)
                .thenComparing(Diagnostic::category)
                .thenComparing(Diagnostic::message));
        return new ScanResult(options.root().toString(), built, diagnostics, metrics.summary());
    }

    private void analyzeCompilationUnit(
            CompilationUnit unit,
            String file,
            String fileId,
            GraphBuilder graph,
            List<Diagnostic> diagnostics,
            MutableMetrics metrics,
            MethodIndex methods,
            List<PendingCall> pendingCalls,
            List<PendingResourceLoad> pendingResourceLoads,
            Set<String> projectedCandidates,
            SqlGraphProjector sqlProjector,
            boolean symbolResolution) {
        Map<Node, String> ownerIds = new HashMap<>();
        Map<String, String> variableTypes = collectVariableTypes(unit);

        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            String qualifiedType = qualifiedName(unit, type);
            String typeId = CanonicalIds.type(qualifiedType);
            ownerIds.put(type, typeId);
            graph.addNode(new GraphNode(typeId, NodeKind.JAVA_TYPE, qualifiedType,
                    Map.of("simpleName", type.getNameAsString())));
            graph.addEdge(fileId, EdgeKind.DECLARES, typeId,
                    evidence(file, type, "java-syntax", "JAVA_TYPE", Confidence.HIGH, Map.of()));

            for (FieldDeclaration field : type.getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    String fieldId = CanonicalIds.field(qualifiedType, variable.getNameAsString());
                    ownerIds.put(variable, fieldId);
                    graph.addNode(new GraphNode(fieldId, NodeKind.JAVA_FIELD,
                            qualifiedType + "." + variable.getNameAsString(),
                            Map.of("type", variable.getTypeAsString())));
                    graph.addEdge(typeId, EdgeKind.DECLARES, fieldId,
                            evidence(file, variable, "java-syntax", "JAVA_FIELD", Confidence.HIGH, Map.of()));
                }
            }

            for (MethodDeclaration method : type.getMethods()) {
                List<String> parameterTypes = method.getParameters().stream()
                        .map(parameter -> compactType(parameter.getTypeAsString()))
                        .toList();
                String methodId = CanonicalIds.method(qualifiedType, method.getNameAsString(), parameterTypes);
                ownerIds.put(method, methodId);
                graph.addNode(new GraphNode(methodId, NodeKind.JAVA_METHOD,
                        qualifiedType + "." + method.getNameAsString(),
                        Map.of("arity", Integer.toString(method.getParameters().size()))));
                graph.addEdge(typeId, EdgeKind.DECLARES, methodId,
                        evidence(file, method, "java-syntax", "JAVA_METHOD", Confidence.HIGH, Map.of()));
                methods.add(new MethodInfo(qualifiedType, type.getNameAsString(), method.getNameAsString(),
                        parameterTypes, methodId));
            }
        }

        jpaAnalyzer.analyze(unit, file, graph);
        Map<String, StringExpressionEvaluator.Binding> bindings = collectBindings(unit);

        for (VariableDeclarator variable : unit.findAll(VariableDeclarator.class)) {
            variable.getInitializer().ifPresent(initializer -> {
                String ownerId = ownerId(variable, ownerIds);
                if (ownerId != null) {
                    projectExpression(initializer, ownerId, file, "JAVA_SQL_EXPRESSION", bindings,
                            graph, diagnostics, metrics, projectedCandidates, sqlProjector);
                }
            });
        }

        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            MethodDeclaration caller = call.findAncestor(MethodDeclaration.class).orElse(null);
            if (caller == null) {
                continue;
            }
            String callerId = ownerIds.get(caller);
            if (callerId == null) {
                continue;
            }
            if (SQL_CALLS.contains(call.getNameAsString()) && !call.getArguments().isEmpty()) {
                projectExpression(call.getArgument(0), callerId, file, jdbcKind(call), bindings,
                        graph, diagnostics, metrics, projectedCandidates, sqlProjector);
            }
            if ((call.getNameAsString().equals("getResource")
                    || call.getNameAsString().equals("getResourceAsStream"))
                    && !call.getArguments().isEmpty()
                    && call.getArgument(0).isStringLiteralExpr()
                    && call.getArgument(0).asStringLiteralExpr().asString().endsWith(".sql")) {
                pendingResourceLoads.add(new PendingResourceLoad(
                        callerId,
                        call.getArgument(0).asStringLiteralExpr().asString(),
                        file,
                        range(call.getArgument(0))));
            }
            pendingCalls.add(pendingCall(call, callerId, qualifiedName(unit,
                    caller.findAncestor(TypeDeclaration.class).orElseThrow()), file, variableTypes,
                    symbolResolution, metrics));
        }
    }

    private void projectExpression(
            Expression expression,
            String ownerId,
            String file,
            String kind,
            Map<String, StringExpressionEvaluator.Binding> bindings,
            GraphBuilder graph,
            List<Diagnostic> diagnostics,
            MutableMetrics metrics,
            Set<String> projectedCandidates,
            SqlGraphProjector sqlProjector) {
        StringExpressionEvaluator.Evaluation evaluation = stringEvaluator.evaluate(expression, bindings);
        if (!looksLikeSql(evaluation.text())) {
            return;
        }
        String candidateKey = ownerId + "|" + range(expression) + "|" + evaluation.text();
        if (!projectedCandidates.add(candidateKey)) {
            return;
        }
        metrics.sqlCandidates++;
        if (!evaluation.resolved()) {
            diagnostics.add(new Diagnostic(
                    "DYNAMIC_SQL_UNKNOWN",
                    file,
                    range(expression),
                    "Potential SQL detected but an identifier or fragment depends on a runtime value: "
                            + bounded(evaluation.text()),
                    Confidence.UNKNOWN));
            return;
        }
        long sqlStarted = System.nanoTime();
        SqlGraphProjector.ProjectionResult result;
        try {
            result = sqlProjector.project(
                    evaluation.text(),
                    new SqlOrigin(ownerId, file, range(expression), kind, evaluation.confidence()),
                    graph,
                    diagnostics);
        } finally {
            metrics.sqlParsingNanos += System.nanoTime() - sqlStarted;
        }
        if (result.parsed()) {
            metrics.parsedSql++;
        }
        if (result.partial()) {
            metrics.partialSql++;
        }
    }

    private void analyzeSqlResources(
            Path root,
            List<Path> sqlFiles,
            GraphBuilder graph,
            List<Diagnostic> diagnostics,
            MutableMetrics metrics,
            SqlGraphProjector sqlProjector) throws IOException {
        for (Path file : sqlFiles) {
            String relative = displayPath(root, file);
            String resourceId = CanonicalIds.resource(relative);
            graph.addNode(new GraphNode(resourceId, NodeKind.RESOURCE_FILE, relative, Map.of()));
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (SqlFragment fragment : splitSql(content)) {
                if (fragment.sql().isBlank()) {
                    continue;
                }
                metrics.sqlCandidates++;
                SourceRange range = new SourceRange(
                        new SourcePosition(fragment.line(), 1),
                        new SourcePosition(fragment.line(), 1));
                long sqlStarted = System.nanoTime();
                SqlGraphProjector.ProjectionResult result;
                try {
                    result = sqlProjector.project(fragment.sql(),
                            new SqlOrigin(resourceId, relative, range, "SQL_RESOURCE", Confidence.HIGH),
                            graph, diagnostics);
                } finally {
                    metrics.sqlParsingNanos += System.nanoTime() - sqlStarted;
                }
                if (result.parsed()) {
                    metrics.parsedSql++;
                }
                if (result.partial()) {
                    metrics.partialSql++;
                }
            }
            metrics.sampleHeap();
        }
    }

    private static void linkResources(
            Path root,
            List<Path> sqlFiles,
            GraphBuilder graph,
            List<Diagnostic> diagnostics,
            List<PendingResourceLoad> loads) {
        for (PendingResourceLoad load : loads) {
            String requested = load.requestedPath().replace('\\', '/');
            while (requested.startsWith("/")) {
                requested = requested.substring(1);
            }
            final String suffix = requested;
            List<Path> matches = sqlFiles.stream()
                    .filter(path -> displayPath(root, path).equals(suffix)
                            || displayPath(root, path).endsWith("/" + suffix))
                    .toList();
            if (matches.size() != 1) {
                diagnostics.add(new Diagnostic(
                        "SQL_RESOURCE_UNRESOLVED",
                        load.file(),
                        load.range(),
                        matches.isEmpty()
                                ? "SQL resource not found: " + load.requestedPath()
                                : "SQL resource path is ambiguous: " + load.requestedPath(),
                        Confidence.LOW));
                continue;
            }
            String relative = displayPath(root, matches.getFirst());
            String resourceId = CanonicalIds.resource(relative);
            graph.addNode(new GraphNode(resourceId, NodeKind.RESOURCE_FILE, relative, Map.of()));
            graph.addEdge(load.ownerId(), EdgeKind.LOADS_RESOURCE, resourceId,
                    new Evidence(load.file(), load.range(), "java-resource", "CLASSPATH_SQL_RESOURCE",
                            Confidence.HIGH, Map.of("requestedPath", load.requestedPath())));
        }
    }

    private static PendingCall pendingCall(
            MethodCallExpr call,
            String callerId,
            String callerType,
            String file,
            Map<String, String> variableTypes,
            boolean resolveSymbols,
            MutableMetrics metrics) {
        String resolvedOwner = null;
        List<String> resolvedParameters = List.of();
        if (resolveSymbols) {
            long resolutionStarted = System.nanoTime();
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                resolvedOwner = resolved.declaringType().getQualifiedName();
                List<String> parameters = new ArrayList<>();
                for (int index = 0; index < resolved.getNumberOfParams(); index++) {
                    parameters.add(simpleType(resolved.getParam(index).describeType()));
                }
                resolvedParameters = List.copyOf(parameters);
            } catch (RuntimeException ignored) {
                // Resolution is optional; the syntactic fallback is intentionally conservative.
            } finally {
                metrics.symbolResolutionNanos += System.nanoTime() - resolutionStarted;
            }
        }
        String receiverType = call.getScope()
                .filter(NameExpr.class::isInstance)
                .map(NameExpr.class::cast)
                .map(NameExpr::getNameAsString)
                .map(name -> variableTypes.getOrDefault(name, name))
                .orElse(null);
        return new PendingCall(callerId, callerType, call.getNameAsString(), call.getArguments().size(),
                resolvedOwner, resolvedParameters, receiverType, call.getScope().isEmpty(), file, range(call));
    }

    private static void linkCalls(
            GraphBuilder graph,
            List<Diagnostic> diagnostics,
            MutableMetrics metrics,
            MethodIndex methods,
            List<PendingCall> calls) {
        for (PendingCall call : calls) {
            MethodInfo target = null;
            Confidence confidence = Confidence.UNKNOWN;
            EdgeKind kind = EdgeKind.POSSIBLY_CALLS;
            String evidenceKind = "UNRESOLVED_CALL";

            if (call.resolvedOwner() != null) {
                target = methods.unique(call.resolvedOwner(), call.name(), call.arity(), call.resolvedParameters());
                if (target != null) {
                    confidence = Confidence.HIGH;
                    kind = EdgeKind.CALLS;
                    evidenceKind = "SYMBOL_RESOLVED_CALL";
                }
            }
            if (target == null && call.unscoped()) {
                target = methods.unique(call.callerType(), call.name(), call.arity(), List.of());
                if (target != null) {
                    confidence = Confidence.HIGH;
                    kind = EdgeKind.CALLS;
                    evidenceKind = "SAME_TYPE_DIRECT_CALL";
                }
            }
            if (target == null && call.receiverType() != null) {
                target = methods.uniqueBySimpleType(call.receiverType(), call.name(), call.arity());
                if (target != null) {
                    confidence = Confidence.MEDIUM;
                    evidenceKind = "UNIQUE_TYPE_NAME_CALL";
                }
            }

            if (target == null) {
                metrics.unresolvedCalls++;
                continue;
            }
            metrics.resolvedCalls++;
            graph.addEdge(call.callerId(), kind, target.id(),
                    new Evidence(call.file(), call.range(), "java-call", evidenceKind, confidence,
                            Map.of("method", call.name(), "arity", Integer.toString(call.arity()))));
        }
    }

    private static Map<String, StringExpressionEvaluator.Binding> collectBindings(CompilationUnit unit) {
        Map<String, StringExpressionEvaluator.Binding> bindings = new LinkedHashMap<>();
        Set<String> assigned = unit.findAll(AssignExpr.class).stream()
                .map(AssignExpr::getTarget)
                .filter(NameExpr.class::isInstance)
                .map(NameExpr.class::cast)
                .map(NameExpr::getNameAsString)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        for (VariableDeclarator variable : unit.findAll(VariableDeclarator.class)) {
            variable.getInitializer().ifPresent(initializer -> {
                boolean declaredFinal = variable.findAncestor(FieldDeclaration.class)
                        .map(FieldDeclaration::isFinal)
                        .orElseGet(() -> variable.findAncestor(com.github.javaparser.ast.expr.VariableDeclarationExpr.class)
                                .map(com.github.javaparser.ast.expr.VariableDeclarationExpr::isFinal)
                                .orElse(false));
                boolean stable = declaredFinal || !assigned.contains(variable.getNameAsString());
                bindings.putIfAbsent(variable.getNameAsString(),
                        new StringExpressionEvaluator.Binding(initializer, stable));
            });
        }
        return bindings;
    }

    private static Map<String, String> collectVariableTypes(CompilationUnit unit) {
        Map<String, String> types = new LinkedHashMap<>();
        unit.findAll(VariableDeclarator.class).forEach(variable ->
                types.putIfAbsent(variable.getNameAsString(), simpleType(variable.getTypeAsString())));
        unit.findAll(com.github.javaparser.ast.body.Parameter.class).forEach(parameter ->
                types.putIfAbsent(parameter.getNameAsString(), simpleType(parameter.getTypeAsString())));
        return types;
    }

    private static String ownerId(Node node, Map<Node, String> ownerIds) {
        Optional<MethodDeclaration> method = node.findAncestor(MethodDeclaration.class);
        if (method.isPresent()) {
            return ownerIds.get(method.get());
        }
        Optional<VariableDeclarator> variable = node.findAncestor(VariableDeclarator.class);
        return variable.map(ownerIds::get).orElse(null);
    }

    private static JavaParser parser(ScanOptions options, List<Path> javaFiles) {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(languageLevel(options.javaVersion()));
        if (options.symbolResolution()) {
            CombinedTypeSolver solvers = new CombinedTypeSolver();
            solvers.add(new ReflectionTypeSolver(false));
            javaSourceRoots(options.root(), javaFiles).forEach(root -> solvers.add(new JavaParserTypeSolver(root)));
            configuration.setSymbolResolver(new JavaSymbolSolver(solvers));
        }
        return new JavaParser(configuration);
    }

    private static List<Path> javaSourceRoots(Path root, List<Path> javaFiles) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path file : javaFiles) {
            Path current = file.getParent();
            while (current != null && current.startsWith(root)) {
                if (current.getFileName() != null && current.getFileName().toString().equals("java")) {
                    roots.add(current);
                    break;
                }
                current = current.getParent();
            }
        }
        if (roots.isEmpty()) {
            roots.add(root);
        }
        return List.copyOf(roots);
    }

    private static ParserConfiguration.LanguageLevel languageLevel(int version) {
        return switch (version) {
            case 8 -> ParserConfiguration.LanguageLevel.JAVA_8;
            case 11 -> ParserConfiguration.LanguageLevel.JAVA_11;
            case 17 -> ParserConfiguration.LanguageLevel.JAVA_17;
            case 21 -> ParserConfiguration.LanguageLevel.JAVA_21;
            default -> throw new IllegalArgumentException("Unsupported Java source version: " + version);
        };
    }

    private static List<Path> discoverFiles(ScanOptions options) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        List<Path> roots = new ArrayList<>();
        roots.add(options.root());
        roots.addAll(options.additionalRoots());
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("Source root is not a directory: " + root);
            }
            try (var walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> !excluded(root.relativize(path)))
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                        .forEach(path -> files.add(path.toAbsolutePath().normalize()));
            }
        }
        return files.stream().sorted().toList();
    }

    private static boolean excluded(Path relative) {
        for (Path part : relative) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String qualifiedName(CompilationUnit unit, TypeDeclaration<?> type) {
        Deque<String> names = new ArrayDeque<>();
        Node current = type;
        while (current instanceof TypeDeclaration<?> currentType) {
            names.addFirst(currentType.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        String local = String.join(".", names);
        return unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString() + "." + local)
                .orElse(local);
    }

    private static String displayPath(Path root, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(root) ? root.relativize(normalized).toString().replace('\\', '/') : normalized.toString();
    }

    private static Evidence evidence(
            String file,
            Node node,
            String analyzer,
            String kind,
            Confidence confidence,
            Map<String, String> details) {
        return new Evidence(file, range(node), analyzer, kind, confidence, details);
    }

    private static SourceRange range(Node node) {
        Range range = node.getRange().orElse(new Range(new Position(1, 1), new Position(1, 1)));
        return new SourceRange(
                new SourcePosition(range.begin.line, range.begin.column),
                new SourcePosition(range.end.line, range.end.column));
    }

    private static boolean looksLikeSql(String text) {
        String normalized = text.replace(StringExpressionEvaluator.DYNAMIC_MARKER, " ")
                .stripLeading()
                .toUpperCase(Locale.ROOT);
        return normalized.startsWith("SELECT ")
                || normalized.startsWith("INSERT ")
                || normalized.startsWith("UPDATE ")
                || normalized.startsWith("DELETE ")
                || normalized.startsWith("MERGE ")
                || normalized.startsWith("WITH ");
    }

    private static String jdbcKind(MethodCallExpr call) {
        return switch (call.getNameAsString()) {
            case "prepareStatement", "prepareCall" -> "JDBC_PREPARED_SQL";
            default -> "JDBC_STATEMENT_SQL";
        };
    }

    private static String compactType(String type) {
        return type.replaceAll("\\s+", "");
    }

    private static String simpleType(String type) {
        String compact = compactType(type);
        int generic = compact.indexOf('<');
        String base = generic >= 0 ? compact.substring(0, generic) : compact;
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    private static String bounded(Object value) {
        String oneLine = String.valueOf(value).replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
    }

    private static List<SqlFragment> splitSql(String content) {
        List<SqlFragment> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int line = 1;
        int startLine = 1;
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            char next = index + 1 < content.length() ? content.charAt(index + 1) : 0;
            if (lineComment) {
                current.append(character);
                if (character == '\n') {
                    lineComment = false;
                    line++;
                }
                continue;
            }
            if (blockComment) {
                current.append(character);
                if (character == '*' && next == '/') {
                    current.append(next);
                    index++;
                    blockComment = false;
                } else if (character == '\n') {
                    line++;
                }
                continue;
            }
            if (quote == 0 && character == '-' && next == '-') {
                lineComment = true;
            } else if (quote == 0 && character == '/' && next == '*') {
                blockComment = true;
            } else if (character == '\'' || character == '"' || character == '`') {
                if (quote == 0) {
                    quote = character;
                } else if (quote == character) {
                    quote = 0;
                }
            }
            if (character == ';' && quote == 0 && !lineComment && !blockComment) {
                fragments.add(new SqlFragment(current.toString(), startLine));
                current.setLength(0);
                startLine = line;
            } else {
                current.append(character);
            }
            if (character == '\n') {
                line++;
                if (current.toString().isBlank()) {
                    startLine = line;
                }
            }
        }
        fragments.add(new SqlFragment(current.toString(), startLine));
        return fragments;
    }

    private record SqlFragment(String sql, int line) {
    }

    private record PendingCall(
            String callerId,
            String callerType,
            String name,
            int arity,
            String resolvedOwner,
            List<String> resolvedParameters,
            String receiverType,
            boolean unscoped,
            String file,
            SourceRange range) {
    }

    private record PendingResourceLoad(
            String ownerId,
            String requestedPath,
            String file,
            SourceRange range) {
    }

    private record MethodInfo(
            String qualifiedType,
            String simpleType,
            String name,
            List<String> parameters,
            String id) {
    }

    private static final class MethodIndex {
        private final List<MethodInfo> methods = new ArrayList<>();

        void add(MethodInfo method) {
            methods.add(method);
        }

        MethodInfo unique(String qualifiedType, String name, int arity, List<String> parameterTypes) {
            List<MethodInfo> matches = methods.stream()
                    .filter(method -> method.qualifiedType().equals(qualifiedType))
                    .filter(method -> method.name().equals(name))
                    .filter(method -> method.parameters().size() == arity)
                    .toList();
            if (matches.size() > 1 && !parameterTypes.isEmpty()) {
                matches = matches.stream()
                        .filter(method -> comparableParameters(method.parameters()).equals(parameterTypes))
                        .toList();
            }
            return matches.size() == 1 ? matches.getFirst() : null;
        }

        MethodInfo uniqueBySimpleType(String type, String name, int arity) {
            String simple = simpleType(type);
            List<MethodInfo> matches = methods.stream()
                    .filter(method -> method.simpleType().equals(simple))
                    .filter(method -> method.name().equals(name))
                    .filter(method -> method.parameters().size() == arity)
                    .toList();
            return matches.size() == 1 ? matches.getFirst() : null;
        }

        private static List<String> comparableParameters(List<String> parameters) {
            return parameters.stream().map(ProjectScanner::simpleType).toList();
        }
    }

    private static final class MutableMetrics {
        private final int javaFiles;
        private int parsedJavaFiles;
        private int javaParseFailures;
        private int resolvedCalls;
        private int unresolvedCalls;
        private int sqlCandidates;
        private int parsedSql;
        private int partialSql;
        private int tables;
        private int columns;
        private int highEdges;
        private int mediumEdges;
        private int lowEdges;
        private int unknownFindings;
        private long durationMillis;
        private long symbolResolutionNanos;
        private long sqlParsingNanos;
        private long peakHeap;

        private MutableMetrics(int javaFiles) {
            this.javaFiles = javaFiles;
            sampleHeap();
        }

        private void sampleHeap() {
            Runtime runtime = Runtime.getRuntime();
            peakHeap = Math.max(peakHeap, runtime.totalMemory() - runtime.freeMemory());
        }

        private void finish(EvidenceGraph graph, List<Diagnostic> diagnostics, long durationMillis) {
            this.durationMillis = durationMillis;
            sampleHeap();
            tables = (int) graph.nodes().stream().filter(node -> node.kind() == NodeKind.DATABASE_TABLE).count();
            columns = (int) graph.nodes().stream().filter(node -> node.kind() == NodeKind.DATABASE_COLUMN).count();
            for (GraphEdge edge : graph.edges()) {
                switch (edge.confidence()) {
                    case HIGH -> highEdges++;
                    case MEDIUM -> mediumEdges++;
                    case LOW -> lowEdges++;
                    case UNKNOWN -> { }
                }
            }
            unknownFindings = (int) diagnostics.stream()
                    .filter(diagnostic -> diagnostic.confidence() == Confidence.UNKNOWN)
                    .count();
        }

        private ScanSummary summary() {
            return new ScanSummary(javaFiles, parsedJavaFiles, javaParseFailures, resolvedCalls, unresolvedCalls,
                    sqlCandidates, parsedSql, partialSql, tables, columns, highEdges, mediumEdges, lowEdges,
                    unknownFindings, durationMillis, Duration.ofNanos(symbolResolutionNanos).toMillis(),
                    Duration.ofNanos(sqlParsingNanos).toMillis(), peakHeap);
        }
    }
}
