# Benchmark results

These are observed single-run measurements, not projected numbers. They are
intended to expose current scaling behavior and make later regressions visible,
not to claim statistically stable performance.

## Environment

- Date: 2026-09-13, America/Sao_Paulo
- OS: Linux 7.0.0-31-generic, x86_64
- CPU: Intel Core i5-1135G7, 4 cores / 8 threads
- RAM: 15 GiB
- JVM: OpenJDK 21.0.12+8, 64-bit
- Heap cap: `-Xmx2g`
- Database Radar: working tree leading to v0.1.0-SNAPSHOT

The scanner's duration is measured inside the process. Wall time and maximum
resident set size (RSS) come from GNU `/usr/bin/time -v`. Accumulated symbol
and SQL times measure the sequential regions spent in those analyzers; they are
subsets of scan duration, not independently parallel phases.

## Results

| Corpus / mode | Java files | Parsed / failed | SQL parsed | Internal duration | Java files/s | Symbol time | SQL time | Approx. heap | Max RSS | Graph size |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Java 8 demo, SQL Server | 5 | 4 / 1 intentional | 8 | 414 ms | 12.1 | 85 ms | 115 ms | 24.5 MiB | 137.2 MiB | 52,372 B |
| Synthetic JDBC, SQL Server | 1,000 | 1,000 / 0 | 1,000 | 2,898 ms | 345.1 | 407 ms | 1,156 ms | 148.4 MiB | 423.1 MiB | 3,632,668 B |
| Apache Maven, symbols off | 3,190 | 3,183 / 7 | 0 | 22,251 ms | 143.4 | 0 ms | 0 ms | 275.1 MiB | 520.9 MiB | 48,983,645 B |
| Apache Maven, symbols on | 3,190 | 3,183 / 7 | 0 | 138,731 ms | 23.0 | 105,006 ms | 0 ms | 1,944.1 MiB | 2,300.0 MiB | 56,818,964 B |

The Java 8 demo deliberately includes one malformed fixture. Its parse failure
and the unresolved dynamic SQL are expected evidence that scanning continues.

## Corpora and commands

The synthetic corpus is generated deterministically by:

```bash
benchmarks/run.sh 1000
```

It creates 1,000 Java files, each with one `PreparedStatement` SELECT over 100
repeated tables. This primarily measures Java discovery, expression evaluation,
SQL Server-mode parsing, graph deduplication, and serialization.

The real corpus was a shallow clone of Apache Maven, Apache License 2.0, at
commit `9ed00db446cf248a579c766cbad4e2322be1f036`:

```bash
java -Xmx2g -jar cli/target/database-radar.jar scan /path/to/maven \
  --java-version 21 --sql-dialect ansi --no-symbol-resolution \
  --output /tmp/maven-no-symbols.json

java -Xmx2g -jar cli/target/database-radar.jar scan /path/to/maven \
  --java-version 21 --sql-dialect ansi \
  --output /tmp/maven-symbols.json
```

The scanner found 3,190 eligible Java files after its default exclusions. The
repository is not a SQL lineage corpus; it is used here to measure Java parsing,
call analysis, incomplete semantic resolution, graph growth, and failure
isolation on a large real tree.

## Interpretation and limits

Symbol solving is currently the dominant scaling risk. On this corpus it added
about 116.5 seconds of internal scan time and approached the 2 GiB heap cap,
while producing 43,961 resolved calls versus 32,301 syntactic resolutions.
The no-symbol mode is therefore a useful explicit fallback, not the default:
it is faster and leaner but converts more links to conservative
`POSSIBLY_CALLS` edges.

SQL parsing took 1,156 ms accumulated for 1,000 simple statements in the
synthetic run. This does not predict complex T-SQL cost. The run order, JVM
warm-up, filesystem cache, CPU frequency scaling, and other host activity were
not controlled. Each row is one run, so comparisons should be treated as
engineering signals rather than a formal performance study.

No incremental cache is implemented. The next performance work should first
profile symbol solving and graph memory, then establish repeated-run variance
before accepting optimizations.
