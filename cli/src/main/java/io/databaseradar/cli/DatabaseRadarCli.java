package io.databaseradar.cli;

import io.databaseradar.graph.CanonicalIds;
import io.databaseradar.graph.GraphDocument;
import io.databaseradar.graph.GraphEdge;
import io.databaseradar.graph.GraphNode;
import io.databaseradar.graph.NodeKind;
import io.databaseradar.java.ProjectScanner;
import io.databaseradar.java.ScanOptions;
import io.databaseradar.report.GraphJson;
import io.databaseradar.report.GraphQueries;
import io.databaseradar.report.HumanReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "dbr",
        description = "Evidence-first Java/database impact analysis.",
        mixinStandardHelpOptions = true,
        version = "Database Radar 0.1.0-SNAPSHOT",
        subcommands = {
                DatabaseRadarCli.ScanCommand.class,
                DatabaseRadarCli.TablesCommand.class,
                DatabaseRadarCli.TableCommand.class,
                DatabaseRadarCli.ColumnCommand.class,
                DatabaseRadarCli.ReadersCommand.class,
                DatabaseRadarCli.WritersCommand.class,
                DatabaseRadarCli.ImpactCommand.class,
                DatabaseRadarCli.MethodCommand.class,
                DatabaseRadarCli.PathCommand.class,
                DatabaseRadarCli.ExportCommand.class
        })
public final class DatabaseRadarCli implements Runnable {
    static final String DEFAULT_GRAPH = ".database-radar/graph.json";

    public static void main(String[] args) {
        CommandLine command = new CommandLine(new DatabaseRadarCli());
        command.setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
            commandLine.getErr().println("error: " + exception.getMessage());
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        });
        System.exit(command.execute(args));
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Command(name = "scan", description = "Scan a Java repository without executing it.")
    static final class ScanCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Repository root")
        Path root;

        @Option(names = "--output", description = "Graph output (default: ${DEFAULT-VALUE})")
        Path output = Path.of(DEFAULT_GRAPH);

        @Option(names = "--java-version", description = "Source syntax: 8, 11, 17, or 21 (default: ${DEFAULT-VALUE})")
        int javaVersion = 8;

        @Option(names = "--source-root", description = "Additional source/resource root", arity = "1")
        List<Path> additionalRoots = new ArrayList<>();

        @Option(names = "--json", description = "Also print the complete graph JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            ScanOptions options = new ScanOptions(root, javaVersion, additionalRoots);
            var scan = new ProjectScanner().scan(options);
            GraphDocument document = new GraphDocument(
                    "1.0", "0.1.0-SNAPSHOT", scan.scannedRoot(), Instant.now().toString(),
                    scan.summary(), scan.graph().nodes(), scan.graph().edges(), scan.diagnostics());
            GraphJson graphJson = new GraphJson();
            graphJson.write(document, output);
            System.out.print(new HumanReport().scanSummary(document, output.toAbsolutePath().normalize().toString()));
            if (json) {
                System.out.println(graphJson.toJson(document));
            }
            return 0;
        }
    }

    private static final class GraphOptions {
        @Option(names = "--graph", description = "Graph input (default: ${DEFAULT-VALUE})")
        Path graph = Path.of(DEFAULT_GRAPH);

        @Option(names = "--json", description = "Print machine-readable JSON")
        boolean json;
    }

    @Command(name = "tables", description = "List discovered database tables.")
    static final class TablesCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Override
        public Integer call() throws Exception {
            GraphDocument document = read(options);
            List<GraphNode> tables = document.nodes().stream()
                    .filter(node -> node.kind() == NodeKind.DATABASE_TABLE)
                    .sorted(Comparator.comparing(GraphNode::id))
                    .toList();
            print(options, tables, new HumanReport().nodes(tables));
            return 0;
        }
    }

    @Command(name = "table", description = "Show reads, writes, and mappings for a table.")
    static final class TableCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String table;

        @Override
        public Integer call() throws Exception {
            return showTarget(options, "table", table);
        }
    }

    @Command(name = "column", description = "Show reads, writes, and mappings for a column.")
    static final class ColumnCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String column;

        @Override
        public Integer call() throws Exception {
            return showTarget(options, "column", column);
        }
    }

    @Command(name = "readers", description = "List code/resource readers of a table or column.")
    static final class ReadersCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String target;

        @Override
        public Integer call() throws Exception {
            return showAccess(options, target, false);
        }
    }

    @Command(name = "writers", description = "List code/resource writers of a table or column.")
    static final class WritersCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String target;

        @Override
        public Integer call() throws Exception {
            return showAccess(options, target, true);
        }
    }

    @Command(name = "impact", description = "Show direct and incoming call paths to a database target.")
    static final class ImpactCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0", description = "table or column")
        String kind;

        @Parameters(index = "1")
        String target;

        @Option(names = "--depth", description = "Maximum incoming call depth (default: ${DEFAULT-VALUE})")
        int depth = 5;

        @Override
        public Integer call() throws Exception {
            GraphDocument document = read(options);
            GraphQueries queries = new GraphQueries(document.graph());
            GraphNode node = queries.databaseTarget(kind, target)
                    .orElseThrow(() -> new IllegalArgumentException("Database target not found: " + target));
            var paths = queries.impact(node.id(), depth);
            print(options, paths, new HumanReport().impact(node.label(), paths, queries));
            return paths.isEmpty() ? 3 : 0;
        }
    }

    @Command(name = "method", description = "Show outgoing graph relations for a Java method.")
    static final class MethodCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String method;

        @Override
        public Integer call() throws Exception {
            GraphDocument document = read(options);
            GraphQueries queries = new GraphQueries(document.graph());
            String id = queries.resolveNodeId(method.startsWith("method:") ? method : "method:" + method);
            List<GraphEdge> edges = document.graph().outgoing(id);
            if (options.json) {
                System.out.println(new GraphJson().toJson(edges));
            } else {
                System.out.println(queries.label(id));
                edges.forEach(edge -> System.out.printf("  -> %s %s  confidence: %s%n",
                        edge.edge(), queries.label(edge.to()), edge.confidence()));
            }
            return 0;
        }
    }

    @Command(name = "path", description = "Find a directed path between two graph nodes.")
    static final class PathCommand implements Callable<Integer> {
        @Mixin
        GraphOptions options;

        @Parameters(index = "0")
        String from;

        @Parameters(index = "1")
        String to;

        @Option(names = "--depth", description = "Maximum depth (default: ${DEFAULT-VALUE})")
        int depth = 10;

        @Override
        public Integer call() throws Exception {
            GraphDocument document = read(options);
            GraphQueries queries = new GraphQueries(document.graph());
            var path = queries.path(from, to, depth);
            if (path.isEmpty()) {
                System.out.println("No path found.");
                return 3;
            }
            if (options.json) {
                System.out.println(new GraphJson().toJson(path.orElseThrow()));
            } else {
                path.orElseThrow().forEach(node -> System.out.println(queries.label(node)));
            }
            return 0;
        }
    }

    @Command(name = "export", description = "Copy the versioned graph JSON.")
    static final class ExportCommand implements Callable<Integer> {
        @Option(names = "--graph", description = "Graph input (default: ${DEFAULT-VALUE})")
        Path graph = Path.of(DEFAULT_GRAPH);

        @Parameters(index = "0")
        Path destination;

        @Override
        public Integer call() throws Exception {
            new GraphJson().read(graph);
            Path parent = destination.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(graph, destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(destination.toAbsolutePath().normalize());
            return 0;
        }
    }

    private static int showTarget(GraphOptions options, String kind, String value) throws Exception {
        GraphDocument document = read(options);
        GraphQueries queries = new GraphQueries(document.graph());
        GraphNode target = queries.databaseTarget(kind, value)
                .orElseThrow(() -> new IllegalArgumentException("Database target not found: " + value));
        var writers = queries.accesses(target.id(), true);
        var readers = queries.accesses(target.id(), false);
        var mappings = queries.mappings(target.id());
        if (options.json) {
            System.out.println(new GraphJson().toJson(new TargetReport(target, writers, readers, mappings)));
        } else {
            HumanReport report = new HumanReport();
            System.out.println("ENTITY: " + target.label());
            System.out.println();
            System.out.print(report.accesses("WRITERS", writers));
            System.out.println();
            System.out.print(report.accesses("READERS", readers));
            System.out.println();
            System.out.println("MAPPINGS");
            if (mappings.isEmpty()) {
                System.out.println("  none");
            } else {
                mappings.forEach(mapping -> System.out.printf("  %s  confidence: %s%n",
                        mapping.originLabel(), mapping.confidence()));
            }
        }
        return 0;
    }

    private static int showAccess(GraphOptions options, String value, boolean writes) throws Exception {
        GraphDocument document = read(options);
        GraphQueries queries = new GraphQueries(document.graph());
        GraphNode target = resolveAutoTarget(queries, value);
        var findings = queries.accesses(target.id(), writes);
        print(options, findings, new HumanReport().accesses(writes ? "WRITERS" : "READERS", findings));
        return findings.isEmpty() ? 3 : 0;
    }

    private static GraphNode resolveAutoTarget(GraphQueries queries, String value) {
        if (value.contains(".")) {
            var column = queries.databaseTarget("column", value);
            if (column.isPresent()) {
                return column.orElseThrow();
            }
        }
        return queries.databaseTarget("table", value)
                .orElseThrow(() -> new IllegalArgumentException("Database target not found: " + value));
    }

    private static GraphDocument read(GraphOptions options) throws Exception {
        return new GraphJson().read(options.graph);
    }

    private static void print(GraphOptions options, Object jsonValue, String humanValue) throws Exception {
        if (options.json) {
            System.out.println(new GraphJson().toJson(jsonValue));
        } else {
            System.out.print(humanValue);
        }
    }

    private record TargetReport(
            GraphNode target,
            List<GraphQueries.AccessFinding> writers,
            List<GraphQueries.AccessFinding> readers,
            List<GraphQueries.MappingFinding> mappings) {
    }
}
