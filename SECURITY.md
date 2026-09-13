# Security policy

## Supported versions

Until the first stable release, security fixes are provided on the `main`
branch only.

## Reporting a vulnerability

Use GitHub private vulnerability reporting for this repository when available.
Do not open a public issue for a parser denial of service, path traversal,
unsafe file write, target-code execution, classloading, or unintended network
access. Include a minimal inert reproducer, affected commit, Java version, and
observed command/output. Do not include production source or credentials.

## Threat model

Database Radar reads potentially hostile Java and SQL text. Its invariant is
that scanning must not execute builds, target bytecode, annotation processors,
SQL, or database connections. It does not follow directory symlinks through its
default file walk and writes only to the explicit graph/export destination.

Resource exhaustion from adversarial parser input remains a known risk. Scan
untrusted repositories with operating-system CPU/memory limits when the source
is not controlled by you.
