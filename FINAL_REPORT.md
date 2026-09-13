# Database Radar v0.1 release-candidate report

## Status

Database Radar has an executable MVP and release-candidate evidence. It is an
independent, offline Java 21 CLI that analyzes Java 8+ source without compiling,
executing, or classloading the target repository. It uses neither an LLM nor a
paid/runtime API.

This report describes behavior observed locally. The hosted
[CI run 34734698989](https://github.com/felipemacedo1/database-radar/actions/runs/34734698989)
created for commit `d226447` did not execute any workflow steps: GitHub marked
the job failed because the account was locked over a billing issue. This is not
test evidence and is not treated as a product failure; the workflow remains a
reproducible recipe.

## Implemented behavior

- syntactic Java parsing with partial results when imports/dependencies are
  missing or another file is malformed;
- JDBC SQL discovery in literals, stable constants, and supported
  concatenations;
- visible UNKNOWN diagnostics for runtime-dependent SQL fragments;
- SELECT/INSERT/UPDATE/DELETE table and simple column extraction;
- SQL Server-first mode for bracketed multipart identifiers, `TOP`,
  `@variables`, aliases, joins, and `UPDATE ... FROM`;
- explicit common-parser modes for PostgreSQL, MySQL/MariaDB, and ANSI-like SQL;
- standalone `.sql` analysis and literal classpath resource links;
- basic `javax.persistence` and `jakarta.persistence` entity/table/column
  mappings without loading persistence classes;
- source method declarations, direct/syntactic calls, symbol-resolved calls,
  and conservative `POSSIBLY_CALLS`;
- readers, writers, impact, path, method, table, and column CLI queries;
- deterministic evidence graph JSON with schema version `1.0`, source ranges,
  analyzer provenance, confidence, and diagnostics;
- optional `--no-symbol-resolution` for lower resource use on large trees;
- shaded executable JAR and SHA-256 release script.

## Architecture decisions

- JavaParser 3.28.2 provides syntax-first parsing with optional Symbol Solver.
  The selection and alternatives are recorded in ADR 0001.
- JSqlParser 5.3 is a syntactic frontend behind Database Radar's conservative
  extraction policy. SQL Server bracket handling is explicitly configured.
  Unsupported/ambiguous constructs do not receive invented edges. See ADR 0002.
- The graph is held in memory and exported as deterministic JSON. SQLite and
  graph databases are deferred until measurements justify them. See ADR 0003.
- Modules are internal separation in one process, not services.

## Verified acceptance evidence

| Criterion | Evidence |
| --- | --- |
| Independent project | Dedicated Git repository and Maven coordinates under `io.databaseradar`; no Flight Recorder dependency |
| Offline runtime | Shaded CLI scans with no database, LLM, network client, target build, or target execution |
| Java 8 target source | Dedicated demo compiles with Maven `--release 8`; Java 8 parser fixture passes |
| Read detection | Demo JDBC SELECT and SQL resource produce `READS_TABLE` / `READS_COLUMN` |
| Write detection | Demo INSERT, UPDATE, and DELETE produce write edges |
| Basic columns | Alias-qualified SELECT and DML target columns are asserted in tests |
| JPA mappings | Explicit/default `@Table` and `@Column` cases are asserted |
| Evidence/confidence | Every edge model requires evidence; source line and confidence have assertions |
| Dynamic SQL honesty | Runtime table name produces `DYNAMIC_SQL_UNKNOWN` and no fabricated database edge |
| Broken project tolerance | Malformed Java and missing imports coexist with useful scan output |
| CLI queries | readers/writers/impact run against the Java 8 demo with file/line evidence |
| Call path | `PedidoService.processar -> PedidoDAO -> SQL -> PEDIDO.STATUS` is emitted |
| Versioned export | Generated demo graph validates against `docs/graph-schema-v1.json` |
| Automated tests | Local Maven reactor verification passes; see reproducible commands below |
| Benchmark | Small, synthetic 1,000-file, and real 3,190-file measurements are recorded |

## SQL and Java compatibility actually tested

Java source fixtures cover Java 8 lambdas, Java 11 `var`, Java 17 records, and
Java 21 pattern matching in `switch`. Parser support beyond these fixtures is
not a product compatibility claim.

SQL tests cover basic aliases/JOIN, schema-qualified and quoted identifiers,
SELECT, INSERT, UPDATE, DELETE, invalid SQL, and SQL Server bracket/TOP/named
variable/UPDATE-FROM cases. SQL Server is the primary dialect. PostgreSQL,
MySQL/MariaDB, and ANSI-like modes currently mean only the documented common
subset, not full vendor compatibility.

## Reproducible verification

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -f demo-legacy-java8/pom.xml clean verify

java -jar cli/target/database-radar.jar scan demo-legacy-java8 \
  --sql-dialect sql-server
java -jar cli/target/database-radar.jar writers PEDIDO.STATUS
java -jar cli/target/database-radar.jar readers PEDIDO.STATUS
java -jar cli/target/database-radar.jar impact column PEDIDO.STATUS
java -jar cli/target/database-radar.jar readers \
  '[dbo].[PEDIDO].[STATUS]'

benchmarks/run.sh 1000
scripts/build-release.sh
```

Observed demo scan:

```text
Java files: 5
Parsed: 4
Parse failures: 1
SQL candidates: 9
SQL parsed: 8
Database tables: 4
Database columns: 10
High confidence edges: 64
Unknown findings: 2
```

The single parse failure and two UNKNOWN findings are intentional demo cases.
See `docs/benchmark-results.md` for timings, memory, graph sizes, environment,
and the exact Apache Maven corpus commit.

## Known failure modes

- SQL built through helper methods, builders, mutable flows, conditionals, or
  runtime identifiers is incomplete; dynamic identifiers remain UNKNOWN.
- Column ownership is withheld when aliases/scopes are ambiguous and no schema
  catalog exists. `SELECT *` does not invent columns.
- CTEs, derived tables, `MERGE` extraction, T-SQL batches/`GO`, temp/table
  variables, procedures, triggers, views, migrations, JPQL/HQL, MyBatis, and
  Hibernate XML are not supported as complete lineage sources.
- Call graphs remain incomplete around reflection, proxies, DI, interfaces,
  polymorphism, method references, and external bytecode.
- Symbol solving without a full target classpath can be expensive and still
  unresolved. On the recorded Apache Maven run it dominated time and approached
  the 2 GiB heap cap. `--no-symbol-resolution` trades precision for a much
  smaller resource footprint.
- Quoted identifier case, database collation, synonyms, and framework naming
  strategies cannot be fully interpreted without database/build context.
- Hostile parser inputs can consume CPU or memory; use OS limits for untrusted
  repositories.
- No incremental cache exists.

## Release boundary and next steps

The v0.1 boundary is a usable evidence-first CLI, not complete SQL lineage.
Before a stable 1.0, the next work should be driven by real SQL Server legacy
repositories: collect false-negative/ambiguity fixtures, profile symbol solving,
improve bounded expression flow, and decide whether schema metadata or a local
cache earns its complexity. Future dynamic-evidence interoperability should use
the documented graph concepts, with no repository or shared-library dependency
on Legacy Flight Recorder.
