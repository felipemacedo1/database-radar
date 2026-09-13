# Analysis model

## Canonical graph

Node IDs are UTF-8 strings with a stable kind prefix:

- `file:<repo-relative-path>`
- `resource:<repo-relative-path>`
- `type:<fully-qualified-binary-like-name>`
- `method:<fully-qualified-type>#<name>(<parameter-type-or-?>,...)`
- `field:<fully-qualified-type>#<name>`
- `entity:<fully-qualified-type>`
- `sql:<sha256-of-normalized-text>`
- `table:<NORMALIZED_QUALIFIED_NAME>`
- `column:<NORMALIZED_TABLE>.<NORMALIZED_COLUMN>`

Unquoted database identifiers are normalized to upper case for query matching.
Quoted identifiers preserve exact case and include a `quoted` attribute. The
original spelling remains in node attributes and evidence details. No catalog
or schema is invented.

Node kinds in schema v1 are `SOURCE_FILE`, `RESOURCE_FILE`, `JAVA_TYPE`,
`JAVA_METHOD`, `JAVA_FIELD`, `ENTITY`, `SQL_STATEMENT`, `DATABASE_TABLE`, and
`DATABASE_COLUMN`.

Edge kinds are `DECLARES`, `CALLS`, `POSSIBLY_CALLS`, `CONTAINS_SQL`,
`READS_TABLE`, `WRITES_TABLE`, `READS_COLUMN`, `WRITES_COLUMN`,
`MAPS_TO_TABLE`, `MAPS_TO_COLUMN`, and `LOADS_RESOURCE`.

## Evidence

Every edge has one or more evidence objects:

```json
{
  "file": "src/main/java/com/acme/PedidoDAO.java",
  "range": {
    "start": {"line": 42, "column": 22},
    "end": {"line": 43, "column": 61}
  },
  "analyzer": "java-jdbc-sql",
  "kind": "JDBC_SQL_LITERAL",
  "confidence": "HIGH",
  "details": {"operation": "SELECT"}
}
```

Line and column numbers are one-based. A missing exact end position is encoded
as absent data, never as zero. Evidence details are string pairs so schema v1
can add analyzer-specific explanations without changing edge types.

## SQL relation rules

- A parsed `SELECT` reads tables in `FROM` and `JOIN`.
- A parsed `INSERT` writes its target table and target columns; reads in an
  `INSERT ... SELECT` are separate read edges.
- A parsed `UPDATE` writes the target table and assigned columns; expressions,
  predicates, and joined/subquery sources may produce read edges.
- A parsed `DELETE` writes its target table; predicate/subquery columns are
  reads when ownership is resolvable.
- `SELECT *` produces table reads only.
- An unqualified column maps to a table only when exactly one table is visible
  in the statement scope. Otherwise the statement records an ambiguity.

## Impact traversal

For a table or column target:

1. Select direct incoming read, write, and mapping edges.
2. Find the containing Java methods through `CONTAINS_SQL` or mapping ownership.
3. Traverse incoming `CALLS` and `POSSIBLY_CALLS` edges breadth-first to the
   configured depth (default 5).
4. Keep a per-path visited set to stop cycles.
5. Deduplicate identical node sequences; do not collapse distinct evidence for
   the same edge.
6. Sort direct writers, direct readers, mappings, then indirect paths by path
   confidence descending, distance ascending, and canonical ID.

Path confidence is the weakest edge confidence. Any `POSSIBLY_CALLS` edge caps
the path at MEDIUM. This is an explanation rule, not an impact score.

## Schema versioning

The root JSON object has `schemaVersion: "1.0"`, tool metadata, scan metadata,
sorted node/edge arrays, and diagnostics. Additive attributes are allowed in a
minor schema version. Removing/renaming fields or changing ID semantics requires
a new major schema version. Consumers must reject unsupported major versions.
