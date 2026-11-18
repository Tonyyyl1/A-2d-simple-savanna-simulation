#!/usr/bin/env sh
# Web client checks (plan v3.2 V2): compile, export the reference world and frame, run the Node tests.
set -eu
ROOT="$(cd "$(dirname "$0")" && pwd)"
command -v node >/dev/null 2>&1 || { echo 'check-web.sh: Node.js is required (https://nodejs.org)' >&2; exit 1; }
"$ROOT/compile-dev.sh" >/dev/null
OUT="$ROOT/build/web-test"
java -Xmx512m -Djava.awt.headless=true -cp "$ROOT/build/dev-classes:$ROOT/build/dev-test-classes" \
    savanna.WorldExportTool "$OUT"
cd "$ROOT"
WEB_TEST_DIR="$OUT" node --test src/test/web/*.test.mjs
echo 'CHECK WEB PASS'
