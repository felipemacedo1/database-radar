# Contributing

Thank you for helping make legacy database changes safer.

## Development setup

Use JDK 21. Build and run all module tests with:

```bash
./mvnw clean verify
./mvnw -f demo-legacy-java8/pom.xml clean verify
```

Before a pull request, also scan the demo and run its three acceptance queries:

```bash
java -jar cli/target/database-radar.jar scan demo-legacy-java8
java -jar cli/target/database-radar.jar readers PEDIDO.STATUS
java -jar cli/target/database-radar.jar writers PEDIDO.STATUS
java -jar cli/target/database-radar.jar impact column PEDIDO.STATUS
```

## Analysis rules

- Add a focused fixture for every new syntax or framework construct.
- Do not claim a dialect or Java version from upstream documentation alone;
  commit a passing test.
- Never turn parser failure or a runtime-dependent identifier into a confirmed
  database edge.
- Preserve the original file/range and analyzer kind on every edge.
- Prefer a visible LOW/UNKNOWN diagnostic over a clever undocumented heuristic.
- Keep graph output deterministic and evolve it according to schema versioning.
- Do not execute the analyzed repository or introduce network access in the
  scanning runtime.

## Change shape

Keep changes narrow. An analyzer change should normally include the fixture,
unit/integration assertion, documentation boundary, and expected confidence.
Run `git diff --check` and the full reactor before submitting.

Security issues involving malicious input should follow [SECURITY.md](SECURITY.md),
not a public issue.
