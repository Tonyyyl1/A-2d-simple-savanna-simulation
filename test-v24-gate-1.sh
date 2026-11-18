#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
EVIDENCE="${1:-$ROOT/build/v24-gate-1/evidence}"
TEST_CLASSES="$(mktemp -d "${TMPDIR:-/tmp}/v24-gate-1-classes.XXXXXX")"
trap 'rm -rf "$TEST_CLASSES"' EXIT HUP INT TERM

cd "$ROOT"
BUILD_ID=v24-gate1-test-infra ./build.sh
diff -u \
    artifacts/candidates/accepted/core-rebase-reach-and-graze-20260802/MAIN_SOURCE_MANIFEST.sha256 \
    build/metadata/MAIN_SOURCE_MANIFEST.sha256

(
    cd gate-profiles
    shasum -a 256 -c v24-core-rebase-transparency-v1.properties.sha256
    shasum -a 256 -c v24-frame-transparency-v1.properties.sha256
    shasum -a 256 -c v24-br1p2-semantic-v1.properties.sha256
)

javac --release 17 -Xlint:unchecked -Xlint:deprecation \
    -cp build/classes -d "$TEST_CLASSES" \
    $(rg --files src/test/java -g '*.java' | LC_ALL=C sort)

java -Djava.awt.headless=true -Dsavanna.gate.output="$EVIDENCE/v24-gate-1" \
    -cp "build/classes:$TEST_CLASSES" savanna.V24Gate1Tests
java -Djava.awt.headless=true -Dsavanna.gate.output="$EVIDENCE/a1b-core" \
    -cp "build/classes:$TEST_CLASSES" savanna.A1bB0TransactionKernelTests

printf 'V24_GATE_1_NON_ECOLOGY PASS evidence=%s ecology_invocations=0\n' \
    "$EVIDENCE"
