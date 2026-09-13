# Database Radar research

Research snapshot: 2026-09-12. This document records the evidence used for the
initial decisions. Versions are pins for the first implementation, not promises
of compatibility with every release in the listed projects.

## Product boundary

Database Radar is not a faster text search. Text search can find the token
`STATUS`, but cannot reliably say whether it is a selected column, an updated
column, a JPA mapping, a comment, an unrelated identifier, or an unresolved
runtime fragment. Database Radar relates a source construct to a parsed SQL or
mapping construct and keeps the source range, analyzer, and confidence that
justify the edge.

It is not IDE Find Usages. IDE usage search starts with a Java symbol and is
strongest when the project model and classpath resolve. The product starts with
a database table or column, crosses SQL/JDBC/resource/JPA boundaries, tolerates
missing dependencies, exports a durable graph, and explains incomplete results.

It is not SonarQube. SonarQube is a broad code-quality and security platform;
its Java analyzer normally requires compiled bytecode for projects with more
than one Java file. Database Radar is a narrow, offline impact-discovery CLI
whose baseline is useful source-only analysis of broken legacy repositories.
[SonarQube Java analysis documentation](https://docs.sonarsource.com/sonarqube-server/9.9/analyzing-source-code/languages/java)

It is not CodeQL. CodeQL is a powerful general relational query and data-flow
system, but its CLI has separate licensing constraints for closed-source use and
its normal workflow creates a CodeQL database. Database Radar owns a small,
domain-specific evidence schema and a zero-configuration Java/database report.
CodeQL remains a reference for path explanations, not a runtime dependency.
[CodeQL repository and licensing note](https://github.com/github/codeql)

The specific legacy-maintenance problem is: given a database identifier before
a change, identify the Java methods, SQL resources, mappings, and upstream call
paths that deserve review, while distinguishing proven structure from partial
or runtime-dependent evidence.

## Java analysis engines

| Candidate | Java 8 input | Broken/no-classpath behavior | Symbols/calls | Source location | Operational fit | License and maintenance | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- |
| JavaParser 3.28.2 + Symbol Solver | Explicit `JAVA_8` language level; parser supports Java 1-25 | Syntactic AST works without compiling; symbol resolution can be attempted per use and failures can be retained | Optional source/reflection/JAR type solvers; direct calls can be resolved or downgraded | Nodes expose ranges | Small embeddable Java API; straightforward visitors; resolution can be layered after syntax | Apache-2.0 or LGPL; 3.28.2 released 2026-05-31 | **Chosen for spike** |
| Spoon 11.5.0 | Configurable compliance level | First-class `noclasspath`; unresolved references remain representable | Rich typed metamodel and executable references; declarations can be null in no-classpath mode | `SourcePosition` model | Excellent mining API, but a larger model and ECJ-based pipeline than the MVP needs | MIT or CeCILL-C; active 11.5.0 release | Strong fallback if the spike exposes recovery/model gaps |
| Eclipse JDT Core | Compiler options support legacy source levels | Statement/binding recovery can retain partial structures and incomplete bindings | Deep compiler-grade bindings; explicit setup required | Compilation unit line/column APIs | Lowest-level and most configurable; binding ASTs have documented time/space cost and integration complexity | EPL-2.0; actively maintained | Not selected for the first vertical slice |
| OpenRewrite 8.92.1 | Java parser variants cover old source levels | Parsing exists, but high-quality type attribution expects a complete compilation classpath | Excellent attributed LST and method matching when dependencies are present | Lossless tree with markers | Optimized for safe transformation/recipes; materially heavier than a read-only evidence extractor | Core Java modules Apache-2.0; some ecosystem recipes use other licenses | Reference, not MVP engine |
| srcML 1.1.0 | Supports Java syntax | Robust text-to-XML conversion without project compilation | Structural XML, no comparable Java symbol solver | Optional position markup | Multi-language and scalable, but adds a native/CLI boundary and XML traversal; weaker Java semantics | GPL-3.0; 1.1.0 released 2025-08 | Rejected for the Java-first embeddable core |

Primary evidence:

- [JavaParser repository, supported language range, setup, and dual license](https://github.com/javaparser/javaparser)
- [JavaParser language-level definitions](https://github.com/javaparser/javaparser/blob/master/javaparser-core/src/main/java/com/github/javaparser/ParserConfiguration.java)
- [Spoon no-classpath model](https://github.com/INRIA/spoon/blob/master/doc/launcher.md)
- [Spoon API no-classpath contract](https://spoon.gforge.inria.fr/mvnsites/spoon-core/apidocs/spoon/compiler/Environment.html)
- [Eclipse JDT `ASTParser`, binding recovery, and cost warning](https://help.eclipse.org/latest/ntopic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html)
- [OpenRewrite type-attribution requirements](https://docs.openrewrite.org/concepts-and-explanations/type-attribution)
- [OpenRewrite licensing](https://docs.openrewrite.org/licensing/openrewrite-licensing)
- [srcML capabilities, release, and GPL statement](https://www.srcml.org/)

### Java-engine risk to prove

JavaParser may parse a compilation unit successfully while individual symbol
lookups fail. The implementation must never wrap a file in an all-or-nothing
symbol-resolution transaction. It will collect declarations, literals, JPA
annotations, and conservative syntactic calls first; resolved calls are an
optional enrichment. If source recovery on malformed files is materially worse
than required, Spoon no-classpath is the first alternative to benchmark.

## SQL parser candidates

| Candidate | Strength | Limitation for this product | License | Decision |
| --- | --- | --- | --- | --- |
| JSqlParser 5.3 | Java-native DML AST and visitors; handles bind parameters, nested selects, aliases, joins, multiple statement kinds, and an explicit SQL Server bracket-quotation mode | Parsing is syntactic; column-to-table ownership can remain ambiguous without schema; dialect coverage is not proof of correctness | Apache-2.0 or LGPL-2.1 | **Chosen for spike under Apache-2.0 terms** |
| Apache Calcite | Standalone parser/object model plus validator and extensible operators | Full validation/lineage benefits from schema and Calcite is a much larger dependency; default grammar is its own SQL dialect | Apache-2.0 | Keep as future evaluator for schema-aware analysis |
| jOOQ parser | Broad parser API and dialect configuration | Larger SQL DSL dependency and open/commercial edition boundaries add unnecessary product/licensing surface | Apache-2.0 for OSS edition plus commercial editions | Not selected |
| FoundationDB SQL Parser | Java parser, Apache-2.0 | Last published artifacts and repository activity are too old for the primary parser of a new tool | Apache-2.0 | Rejected |

Primary evidence:

- [JSqlParser grammar, Java runtime matrix, visitors, benchmark, and license](https://github.com/JSQLParser/JSqlParser)
- [JSqlParser SQL Server bracket-quotation configuration](https://github.com/JSQLParser/JSqlParser/blob/master/src/main/java/net/sf/jsqlparser/parser/CCJSqlParserUtil.java)
- [Apache Calcite standalone SQL parser model](https://calcite.apache.org/javadocAggregate/org/apache/calcite/sql/package-summary.html)
- [Calcite default SQL grammar](https://calcite.apache.org/docs/reference.html)
- [jOOQ parser API](https://www.jooq.org/doc/latest/manual/sql-building/sql-parser/sql-parser-api/)
- [FoundationDB parser artifact metadata](https://central.sonatype.com/artifact/com.foundationdb/fdb-sql-parser)

### SQL strategy risk to prove

Microsoft SQL Server is the primary production dialect, clarified during the
MVP. The test matrix therefore leads with bracketed identifiers, `TOP`, named
`@variables`, and `UPDATE ... FROM`, while retaining PostgreSQL, MySQL/MariaDB,
and ANSI-like common SQL as configured modes. Microsoft documents brackets and
double quotes as delimited identifiers and `TOP` in the SELECT grammar.
[SQL Server identifiers](https://learn.microsoft.com/en-us/sql/relational-databases/databases/database-identifiers?preserve-view=true&version=fabric&view=sql-server-ver16),
[SQL Server SELECT clause](https://learn.microsoft.com/en-us/sql/t-sql/queries/select-clause-transact-sql?view=sql-server-ver17)

The AST does not supply a database catalog. The MVP may state that
`p.STATUS` belongs to `PEDIDO p` when an alias scope is syntactically unique,
but unqualified columns across multiple tables must remain unresolved or point
only to a statement-level unknown. `SELECT *` proves a table read, not a list of
all columns. Parser failure is a finding, not permission to infer facts from
keywords.

## Adjacent tools and lineage systems

- Semgrep Community Edition is useful for quick syntax patterns, but its own
  documentation describes community analysis as function/file bounded; it does
  not provide this product's cross-artifact database graph.
  [Semgrep repository](https://github.com/semgrep/semgrep)
- CodeQL demonstrates explainable path queries and a queryable code database,
  but is intentionally broader and subject to CLI usage terms.
  [CodeQL query concepts](https://codeql.github.com/docs/writing-codeql-queries/about-codeql-queries/)
- OpenLineage models runtime jobs, runs, and datasets sent by pipeline
  integrations. It is a future interoperability reference, not a Java source
  analyzer or graph backend for the MVP.
  [OpenLineage overview](https://openlineage.io/)
- SonarQube optimizes broad quality/security analysis and bytecode-assisted Java
  semantics, not source-only table/column impact discovery.

## Initial technical risks

1. Dynamic SQL can cross variables, methods, builders, branches, and resource
   loaders. V1 intentionally evaluates only literals, compile-time-like string
   constants, and simple concatenations; every hole is retained.
2. Java overloads are only canonical when a declaration or resolved call gives
   arity/type information. Unresolved syntactic calls become `POSSIBLY_CALLS`.
3. SQL column ownership is ambiguous without a catalog, especially unqualified
   columns in joins and wildcard projections.
4. JPA defaults derive names from type/field names. Explicit names are HIGH;
   derived defaults are MEDIUM and visibly marked as conventions.
5. Parser success is not semantic correctness for vendor SQL. The supported
   matrix is based on repository tests, not the upstream feature list.
6. Holding full ASTs and a graph for thousands of files may dominate memory.
   Visitors must emit compact records and release compilation units.
7. A call graph under reflection, proxies, DI, inheritance, and interface
   dispatch is incomplete by design. V1 reports direct source calls and
   possible calls separately.

## Outcome

Proceed with a Java 21 Maven application that analyzes Java 8+ source using
JavaParser 3.28.2 and parses the documented SQL Server-first, multi-dialect
subset using JSqlParser 5.3.
The first implementation must prove one Java 8 JDBC SELECT from source range to
queryable graph before modules or feature breadth are added.
