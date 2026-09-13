# SQL support v1

Database Radar uses JSqlParser 5.3 as a syntactic frontend and applies its own
conservative extraction rules. Upstream parse support is not automatically a
Database Radar compatibility claim.

## Dialect priority and selection

Microsoft SQL Server is the primary dialect. Use `--sql-dialect sql-server` for
T-SQL source. `auto` prefers SQL Server bracket quotation when `[` occurs,
otherwise it tries the common grammar and then bracket mode. PostgreSQL array
syntax can conflict with bracket heuristics, so use `--sql-dialect postgresql`
for repositories that use arrays. `mysql`/`mariadb` and `ansi` select the common
parser mode without SQL Server fallback.

The SQL Server fixtures are based on the vendor's documented
[identifier delimiters](https://learn.microsoft.com/en-us/sql/relational-databases/databases/database-identifiers?preserve-view=true&version=fabric&view=sql-server-ver16),
[`SELECT TOP` grammar](https://learn.microsoft.com/en-us/sql/t-sql/queries/select-clause-transact-sql?view=sql-server-ver17),
and [`DELETE ... FROM` join extension](https://learn.microsoft.com/en-us/sql/t-sql/statements/delete-transact-sql?view=sql-server-ver17).

## Tested target subset

The v1 acceptance suite covers:

- `SELECT` with aliases, qualified/unqualified columns, `WHERE`, and `JOIN`;
- `INSERT INTO table (columns) VALUES ...` and `INSERT ... SELECT`;
- `UPDATE table SET columns ... WHERE ...`;
- `DELETE FROM table WHERE ...`;
- schema-qualified tables;
- ANSI double-quoted identifiers and MySQL backtick identifiers where the
  parser retains enough information;
- JDBC `?` parameters;
- SQL Server bracketed multipart identifiers, `TOP`, `@variables`, and
  `UPDATE <alias> SET ... FROM <table> <alias> JOIN ...`;
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

SQL Server is the priority; PostgreSQL and MySQL/MariaDB remain viable for the
tested common subset. Oracle, stored procedures, triggers, `MERGE` extraction,
T-SQL batches/`GO`, table variables, temp tables, vendor procedural languages,
JPQL/HQL, migrations, and view expansion are future work.
