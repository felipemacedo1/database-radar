#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

cd "$PROJECT_DIR"
./mvnw --batch-mode --no-transfer-progress clean verify
sha256sum cli/target/database-radar.jar > cli/target/database-radar.jar.sha256
printf '%s\n' "Artifact: $PROJECT_DIR/cli/target/database-radar.jar"
printf '%s\n' "Checksum: $PROJECT_DIR/cli/target/database-radar.jar.sha256"
