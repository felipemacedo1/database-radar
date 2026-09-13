# Roadmap

## v0.1 release candidate

- Evidence graph schema v1 and deterministic JSON.
- Java 8/11/17/21 syntax fixtures, source ranges, partial parse diagnostics.
- JDBC strings, SQL resources, basic JPA, DML table/column access.
- Direct and possible source call paths.
- readers/writers/impact/path/method/table/column CLI queries.
- Reproducible demo, tests, and benchmark evidence.

## After measured v0.1 use

Prioritize from observed false positives/negatives rather than framework count:

1. expression data flow across assignments/helpers and common SQL builders;
2. richer call resolution for interfaces and multi-module classpaths without
   executing builds;
3. dialect-specific PostgreSQL and MySQL/MariaDB fixture matrices;
4. JPQL/HQL as distinct languages;
5. optional hash-based incremental cache, only after benchmarked benefit;
6. migrations, MyBatis, Hibernate XML, and schema-aware wildcard resolution.

## Documented future, not v0.1 scope

- runtime evidence interoperability with Legacy Flight Recorder;
- possible-versus-observed impact comparison;
- commit/diff analysis and breaking-schema policy;
- IDE plugins and a local web visualization;
- stored procedures, triggers, views, and broad vendor SQL;
- optional natural-language explanations.

These items must not create a repository dependency on Legacy Flight Recorder,
an LLM requirement, a paid API, a SaaS, or a graph database in the core.
