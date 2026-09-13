# ADR 0002: SQL parser strategy

- Status: accepted for spike
- Date: 2026-09-12

## Context

Regexes cannot safely distinguish nested SQL, aliases, write targets, or vendor
syntax. Conversely, parser success without schema does not resolve every column.
The tool must expose gaps rather than silently guess.

## Decision

Use JSqlParser 5.3 under its Apache-2.0 option. Extract only explicitly supported
DML shapes with dedicated visitors/tests. Resolve aliases within statement
scope. Assign an unqualified column only when its owner is unique. On parse
failure or dynamic identifier holes, emit UNKNOWN diagnostics and no fabricated
database edge. Keep the SQL analyzer behind an internal interface so a future
dialect-specific or schema-aware parser can coexist.

## Consequences

SELECT/INSERT/UPDATE/DELETE can be proved quickly with a Java-native AST. The
project owns the correctness matrix and must not claim all upstream dialects.
Column lineage across CTEs, derived tables, wildcard expansion, procedures, and
vendor-specific constructs remains limited.

## Alternatives

Calcite is attractive for future schema-aware validation but unnecessarily
large for the first slice. jOOQ adds broad DSL/licensing surface. FoundationDB's
parser is insufficiently maintained. A handwritten SQL grammar is unjustified.
