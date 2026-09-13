# ADR 0003: Graph storage format

- Status: accepted
- Date: 2026-09-12

## Context

The MVP needs offline queries, automation output, evidence portability, and a
future conceptual interchange point with dynamic-analysis tools. It does not
need concurrent writes or a graph server.

## Decision

Use an in-memory adjacency model during a scan and write deterministic JSON with
an explicit `schemaVersion`. Arrays and evidence are sorted for stable diffs and
golden tests. The default path is `.database-radar/graph.json`. Writes use a
temporary sibling followed by an atomic move when supported.

## Consequences

The graph is transparent, portable, reviewable, and works without services.
Loading the full file is acceptable for the measured MVP corpus. SQLite may be
reconsidered only after benchmarks show JSON load/query or memory cost is a real
problem. Neo4j and other external graph databases are excluded.
