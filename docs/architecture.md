# Architecture

## Shape

Database Radar is a modular monolith and one CLI process. Modules are Java/Maven
boundaries, not services:

```text
CLI -> scan orchestration -> Java / SQL / persistence analyzers
                         -> graph-core -> report / JSON export
```

The analyzed repository is input data. No analyzer executes its build, loads
its classes, runs annotation processors, opens a database connection, executes
SQL, or sends source over the network.

## Planned modules after the spike

- `graph-core`: canonical IDs, immutable nodes/edges/evidence, deduplication,
  traversal, schema-v1 serialization contracts.
- `sql-analyzer`: operation classification and conservative table/column
  extraction from resolved SQL text.
- `java-analyzer`: declarations, string-expression evaluation, JDBC evidence,
  resource references, and direct/possible calls.
- `persistence-analyzer`: JPA annotations, initially both `javax.persistence`
  and `jakarta.persistence` names without loading either API.
- `report`: deterministic human and JSON output.
- `cli`: source-root discovery, scan orchestration, storage, and queries.
- `demo-legacy-java8`: inert fixture repository used as analyzer input, not as a
  runtime dependency.

The spike may start in one build module. Module extraction is allowed only after
the vertical proof passes.

## Scan pipeline

1. Normalize and validate the requested root.
2. Select the configured SQL parser mode (`auto`, SQL Server, PostgreSQL,
   MySQL/MariaDB, or ANSI-like); SQL Server bracket quoting is explicit.
3. Discover conventional Java/resource roots plus explicit additional paths;
   skip `.git`, `target`, `build`, `.gradle`, `.idea`, and Radar output.
4. Parse each Java file syntactically and emit declarations/findings even when
   later semantic enrichment fails.
5. Evaluate supported string expressions and associate SQL candidates with the
   smallest enclosing Java method/field.
6. Parse resolved SQL candidates; on failure, emit diagnostic evidence without
   structural table/column claims.
7. Inspect basic JPA annotations directly from syntax.
8. Attempt direct source-call resolution; downgrade unresolved candidates.
9. Parse standalone `.sql` resources and connect literal resource loads where
   supported.
10. Deduplicate nodes/edges by canonical content key while retaining distinct
   evidence records.
11. Write `.database-radar/graph.json` atomically and print scan diagnostics.

Build files are never executed or used to resolve dependencies in v1. Source
discovery is based on the configured root, recursive file discovery, and any
explicit `--source-root` values.

## Failure isolation

Every source file is an independent unit of work. A malformed file increments a
parse-failure diagnostic and does not abort other files. SQL statements are
independent candidates. Symbol-resolution exceptions are counted and do not
erase syntax-derived evidence.

## Security boundary

- Files are read as bytes/text only under configured roots.
- Symlink traversal is disabled by default.
- Build files and repository scripts are never executed; v1 does not use them
  for dependency resolution.
- No network client exists in the runtime dependency graph.
- Resource and output paths are normalized to prevent writes outside the chosen
  output location.
- SQL is parsed as text and never sent to JDBC.

## Performance shape

The baseline is a streaming file walk and per-file AST lifetime. Graph records
are compact values held in memory. Symbol enrichment can be disabled with
`--no-symbol-resolution` and timed separately. The benchmark records wall time,
Java files/second, approximate peak heap, accumulated symbol-resolution and SQL
parse time, and serialized graph size. No cache is part of v1.
