#!/usr/bin/env sh
# Records a headless run for client development: scripts/record-fixture.sh <seed> <steps> <output.savrec>
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SEED="${1:?usage: record-fixture.sh <seed> <steps> <output-file>}"
STEPS="${2:?}"
OUT="${3:?}"
"$ROOT/compile-dev.sh" >/dev/null
exec java -Xmx1g -Djava.awt.headless=true -cp "$ROOT/build/dev-classes:$ROOT/build/dev-test-classes" \
    savanna.RecordFixtureTool "$SEED" "$STEPS" "$OUT"
