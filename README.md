# Database Radar

> Before changing a table or column in a legacy Java system, run Database Radar
> to see which code may depend on it — with file, line, operation, path, and
> confidence.

Database Radar is an offline static-analysis CLI for Java 8+ source. It finds
JDBC SQL, standalone `.sql` resources, basic JPA mappings, and direct source
calls, then builds an evidence graph you can query without compiling or running
the analyzed project.

```text
$ dbr impact column PEDIDO.STATUS
ENTITY: PEDIDO.STATUS

IMPACT PATHS
  com.acme.pedido.PedidoDAO.atualizarStatus -> UPDATE ... -> PEDIDO.STATUS
    confidence: HIGH
  com.acme.pedido.PedidoService.processar -> PedidoDAO.buscarPendentes -> SELECT ... -> PEDIDO.STATUS
    confidence: HIGH
```

Database Radar never turns unresolved dynamic SQL into a confirmed dependency.
`"SELECT * FROM " + tableName` is reported as UNKNOWN with its source location.

## Try it in under 60 seconds

Requirements: JDK 21. The target repository may still contain Java 8 source.

```bash
./mvnw clean package
java -jar cli/target/database-radar.jar scan demo-legacy-java8
java -jar cli/target/database-radar.jar writers PEDIDO.STATUS
java -jar cli/target/database-radar.jar readers PEDIDO.STATUS
java -jar cli/target/database-radar.jar impact column PEDIDO.STATUS
```

The scan writes `.database-radar/graph.json`. For a shell-like command after a
local build, use `bin/dbr` in place of `java -jar ...`.

## Commands

```text
dbr scan <repo> [--java-version 8|11|17|21] [--sql-dialect auto|sql-server|postgresql|mysql|ansi] [--no-symbol-resolution] [--source-root <path>] [--output <file>]
dbr tables [--graph <file>] [--json]
dbr table <TABLE> [--graph <file>] [--json]
dbr column <TABLE.COLUMN> [--graph <file>] [--json]
dbr readers <TABLE-or-COLUMN> [--graph <file>] [--json]
dbr writers <TABLE-or-COLUMN> [--graph <file>] [--json]
dbr impact table <TABLE> [--depth 5] [--graph <file>] [--json]
dbr impact column <TABLE.COLUMN> [--depth 5] [--graph <file>] [--json]
dbr method <qualified-type#method(parameters)> [--graph <file>]
dbr path <from-node> <to-node> [--depth 10] [--graph <file>] [--json]
dbr export <destination> [--graph <file>]
```

`scan` recursively discovers Java and SQL files, including conventional nested
Maven/Gradle modules. `.git`, build output, IDE metadata, and prior Radar output
are ignored. Extra source roots can be repeated with `--source-root`.

## What v0.1 actually understands

- Java string literals, multiline concatenations, stable local values, and
  `static final String` constants;
- `PreparedStatement`/`Statement` calls and SELECT/INSERT/UPDATE/DELETE;
- SQL Server-first tables, simple columns, schema-qualified/bracketed names,
  `TOP`, named variables, aliases, joins, and `UPDATE ... FROM`, plus the tested
  ANSI/PostgreSQL/MySQL common subset;
- standalone `.sql` files and literal classpath resource loads;
- `javax.persistence` and `jakarta.persistence` names for `@Entity`, `@Table`,
  `@Column`, and `@Transient`, without loading those libraries;
- source method declarations, overload-safe IDs, resolved direct calls, and
  conservative `POSSIBLY_CALLS` fallbacks;
- deterministic, versioned JSON and human-readable evidence reports.

See [SQL support](docs/sql-support.md) for the exact boundary and
[compatibility](docs/compatibility.md) for tested source versions.

## Confidence is part of the result

- `HIGH`: explicit and structurally parsed, such as a complete JDBC SQL literal
  or explicit JPA name.
- `MEDIUM`: a narrow documented inference, such as a unique syntactic call or
  JPA default naming.
- `LOW`: a review lead that is insufficient for a confirmed database edge.
- `UNKNOWN`: the target or operation depends on runtime state or failed parsing.

Full rules are in [the confidence model](docs/confidence-model.md). Every graph
edge retains analyzer provenance and source evidence. Diagnostics preserve
parsing gaps instead of hiding them.

## Safety and offline behavior

The runtime does not execute target code, Maven, Gradle, annotation processors,
SQL, or database connections. It does not load target classes, use an LLM, call
a paid API, or send source to a network service. Dependencies are downloaded
only when building Database Radar itself; the packaged JAR scans offline.

Treat analyzed code as untrusted input. Report parser issues privately as
described in [SECURITY.md](SECURITY.md).

## Architecture and decisions

Database Radar is one process with internal Maven modules, not distributed
services:

```text
cli -> java-analyzer -> sql-analyzer / persistence-analyzer
                  \-> graph-core -> report / graph.json
```

- [Research and product differentiation](RESEARCH.md)
- [Architecture](docs/architecture.md)
- [Evidence graph model](docs/analysis-model.md)
- [JSON Schema v1](docs/graph-schema-v1.json)
- [Java engine ADR](docs/adr/0001-java-analysis-engine.md)
- [SQL parser ADR](docs/adr/0002-sql-parser-strategy.md)
- [Graph format ADR](docs/adr/0003-graph-storage-format.md)
- [Roadmap](docs/roadmap.md)

The completed spike remains in `spike/` as executable evidence of the first
vertical proof; it is intentionally outside the release reactor.

## Build, test, and benchmark

```bash
./mvnw clean verify
./mvnw -f demo-legacy-java8/pom.xml clean verify
benchmarks/run.sh 1000
scripts/build-release.sh
```

The last command produces the shaded JAR and a SHA-256 checksum. Recorded
measurements and exact hardware/JVM context live in
[benchmark results](docs/benchmark-results.md). CI is included as a repeatable
recipe, but local evidence is reported separately from hosted CI availability.

## Current limitations

No static call graph is complete. Reflection, proxies, framework DI, complex
polymorphism, SQL builders, JPQL/HQL, procedures, triggers, wildcard expansion,
and vendor procedural SQL remain incomplete or unsupported. JPA default naming
does not apply a framework naming strategy. See [FINAL_REPORT.md](FINAL_REPORT.md)
for the release-candidate evidence and known failure modes.

## Contributing and license

Issues and focused pull requests are welcome; read [CONTRIBUTING.md](CONTRIBUTING.md).
Database Radar is licensed under Apache License 2.0. It was chosen for a reusable
developer tool because it is permissive and includes an explicit patent grant;
the selected JavaParser and JSqlParser dependencies are consumed under their
Apache-2.0 options.
