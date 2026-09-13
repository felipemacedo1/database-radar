# SQL support v1

Database Radar uses JSqlParser 5.3 as a syntactic frontend and applies its own
conservative extraction rules. Upstream parse support is not automatically a
Database Radar compatibility claim.

## Tested target subset

The v1 acceptance suite covers reasonable ANSI SQL shared by PostgreSQL and
MySQL/MariaDB:

- `SELECT` with aliases, qualified/unqualified columns, `WHERE`, and `JOIN`;
- `INSERT INTO table (columns) VALUES ...` and `INSERT ... SELECT`;
- `UPDATE table SET columns ... WHERE ...`;
- `DELETE FROM table WHERE ...`;
- schema-qualified tables;
- ANSI double-quoted identifiers and MySQL backtick identifiers where the
  parser retains enough information;
- JDBC `?` parameters;
- multiline statements and comments;
- one or more statements in `.sql` resource files.

`MERGE` may be recognized as an operation but is not an acceptance requirement
until read/write extraction has dedicated tests.

## Column semantics

The analyzer reports target columns for INSERT/UPDATE and projection/predicate/
join columns for SELECT when table ownership is explicit or uniquely inferable.
It does not expand `*` without a schema. In multi-table scopes, an unqualified
column is retained as ambiguous rather than assigned to every table.

## Dynamic SQL

The Java analyzer hands the SQL analyzer either a resolved string plus
provenance, or an unresolved candidate plus holes. Runtime-dependent table or
column identifiers yield an UNKNOWN diagnostic and no database edge. Runtime
values represented by JDBC parameters are normal parsed SQL, not dynamic
identifier uncertainty.

## Parse failures and dialects

Invalid or unsupported SQL produces a diagnostic containing the source range,
parser class, bounded error message, and confidence UNKNOWN. No silent regex
fallback creates confirmed reads or writes.

PostgreSQL and MySQL/MariaDB are priorities, but only constructs in the test
matrix are claimed. Oracle, SQL Server, stored procedures, triggers, vendor
procedural languages, JPQL/HQL, migrations, and view expansion are future work.
