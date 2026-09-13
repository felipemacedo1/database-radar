# Benchmarks

`run.sh` generates an inert Java corpus in a fresh temporary directory, scans
it with a 2 GiB heap ceiling, prints the Database Radar scan metrics and GNU
`time -v` process metrics, reports graph size, and removes only that validated
temporary directory.

```bash
./mvnw -q package -DskipTests
benchmarks/run.sh 1000
```

The generated classes use Java 8 syntax and one JDBC SELECT each, spread over
100 table names. This measures parser/graph throughput reproducibly; it is not
an accuracy proxy for real legacy source.
