# Compatibility

## Tool runtime

The initial toolchain is OpenJDK 21 and Maven. The intended distribution is a
self-contained runnable JAR; the analyzed project does not need Maven, Gradle,
or Java installed once the tool is running.

## Analyzed Java source

Java 8 is the mandatory baseline. The parser has explicit language levels for
newer Java releases, but Database Radar will claim only versions exercised by
its fixtures. Initial release tests exercise Java 8 lambdas, Java 11 `var`,
Java 17 records, and Java 21 pattern matching in `switch`.
Parser capability beyond that is upstream capability, not yet product support.

The target project may have missing imports, private dependencies, or broken
build files. Syntax-derived declarations, SQL, and JPA annotations should still
be emitted per successfully parsed file. Truly malformed compilation units are
reported and skipped; v1 does not promise token-level recovery inside them.

Both `javax.persistence` (legacy Java 8 priority) and `jakarta.persistence`
annotation names are recognized syntactically. The annotation classes do not
need to be on the analyzer classpath.

## Repository layout

Default discovery includes all nested conventional `src/main/java`,
`src/test/java`, `src/main/resources`, and `src/test/resources` roots, including
multi-module Maven/Gradle repositories. Additional roots can be supplied. The
scanner ignores build output and VCS metadata and never runs build scripts.

## SQL

SQL Server is primary. `--sql-dialect sql-server` enables bracket-delimited
identifiers explicitly; `auto` selects that mode when brackets occur. The suite
also retains ANSI-like, PostgreSQL, and MySQL/MariaDB common constructs. See
[SQL support](sql-support.md). Compatibility means a committed test fixture
passes; it never means every construct accepted by a database vendor is parsed.
