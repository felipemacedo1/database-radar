#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
COUNT=${1:-1000}
WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/database-radar-benchmark.XXXXXX")

cleanup() {
  case "$WORK_DIR" in
    "${TMPDIR:-/tmp}"/database-radar-benchmark.*) rm -rf -- "$WORK_DIR" ;;
  esac
}
trap cleanup EXIT INT TERM

"$SCRIPT_DIR/generate-synthetic-corpus.sh" "$COUNT" "$WORK_DIR/corpus"
if [ ! -f "$PROJECT_DIR/cli/target/database-radar.jar" ]; then
  "$PROJECT_DIR/mvnw" -q -f "$PROJECT_DIR/pom.xml" package -DskipTests
fi

printf '%s\n' "java_files=$COUNT"
printf '%s\n' "java_version=$(java -version 2>&1 | head -n 1)"
/usr/bin/time -v java -Xmx2g -jar "$PROJECT_DIR/cli/target/database-radar.jar" \
  scan "$WORK_DIR/corpus" --output "$WORK_DIR/graph.json" --sql-dialect sql-server
printf '%s\n' "graph_bytes=$(wc -c < "$WORK_DIR/graph.json")"
