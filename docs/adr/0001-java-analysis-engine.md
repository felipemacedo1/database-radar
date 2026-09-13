# ADR 0001: Java analysis engine

- Status: accepted for spike
- Date: 2026-09-12

## Context

The analyzer must read Java 8+ source, preserve ranges, and produce partial value
when classpaths/builds are missing. Full semantic resolution is useful but
cannot be a prerequisite.

## Decision

Use JavaParser 3.28.2 for the syntax-first model and its Symbol Solver only as
an optional enrichment. Walk and release one compilation unit at a time. Catch
resolution failures at the individual call/reference boundary. Encode a direct
source resolution as `CALLS`; encode only narrowly inferred candidates as
`POSSIBLY_CALLS`.

## Consequences

The core stays embeddable and does not invoke the target compiler. Some malformed
files and advanced overload/polymorphism cases will remain unresolved. A
fixture-based spike must validate Java 8 parsing, ranges, JDBC association, and
partial operation without dependencies. If recovery is inadequate, benchmark
Spoon no-classpath before changing the graph contracts.

## Alternatives

Spoon offers first-class no-classpath analysis but has a heavier metamodel; JDT
offers compiler-grade recovery at higher integration and memory cost;
OpenRewrite focuses on attributed transformations; srcML adds an external GPL
XML pipeline with weaker Java semantics for this use case.
