# Java 8 demo corpus

This inert project exists to be scanned. It contains direct JDBC, all four MVP
DML operations, a join, Java constants and concatenation, an unresolved dynamic
table, a classpath SQL resource, basic `javax.persistence` mappings, and a
service-to-DAO call path.

The malformed file under `fixtures/broken` is outside the Maven source tree so
the demo can compile, but Database Radar still sees it when scanning the demo
root and demonstrates partial analysis.
