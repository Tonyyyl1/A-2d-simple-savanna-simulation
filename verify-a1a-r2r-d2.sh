#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$ROOT/artifacts/history/investigations/diagnostics/a1a-r2r-d2-20260727/evidence}"
REFERENCE="$ROOT/artifacts/candidates/accepted/a1a-r2-single-position-board-20260724-perf/savanna-simulation-v1.8-a1a-r2-single-position-board-20260724-perf-dev.jar"

if test -e "$OUT"
then
    printf 'D2 evidence directory already exists: %s\n' "$OUT" >&2
    exit 1
fi
test -f "$REFERENCE"
mkdir -p "$OUT"

cd "$ROOT"
BUILD_ID="a1a-r2r-d2-diagnostic" ./build.sh > "$OUT/BUILD.txt" 2>&1
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/savanna-a1a-r2r-d2.XXXXXX")"
find src/test/java -type f -name '*.java' -print | LC_ALL=C sort \
    > "$TMP_DIR/test-sources.list"
javac --release 17 -Xlint:unchecked -Xlint:deprecation -cp build/classes \
    -d "$TMP_DIR/test-classes" @"$TMP_DIR/test-sources.list" \
    > "$OUT/TEST_COMPILE.txt" 2>&1
CURRENT_CP="build/classes:$TMP_DIR/test-classes"
REFERENCE_CP="$REFERENCE:$TMP_DIR/test-classes"

java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.A1aR2rD2Tests \
    > "$OUT/D2_FIXTURES.txt" 2>&1
grep -q 'A1A_R2R_D2_FIXTURES PASS' "$OUT/D2_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.PositionKernelTests \
    > "$OUT/R1_KERNEL_FIXTURES.txt" 2>&1
grep -q 'A1A_R1_KERNEL_FIXTURES PASS' "$OUT/R1_KERNEL_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.A1aR2PositionBoardTests \
    > "$OUT/R2_POSITION_BOARD_FIXTURES.txt" 2>&1
grep -q 'A1A_R2_POSITION_BOARD_FIXTURES PASS' \
    "$OUT/R2_POSITION_BOARD_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.WaterSafetyProbe \
    300 100 baseline > "$OUT/WATER_SAFETY_300.txt" 2>&1
java -Djava.awt.headless=true -cp "$CURRENT_CP" \
    savanna.WaterBehaviorRegressionProbe --repeat 100 \
    > "$OUT/WATER_BEHAVIOR_REPEAT_100.txt" 2>&1
grep -q 'O0A_REPEATABILITY PASS' "$OUT/WATER_BEHAVIOR_REPEAT_100.txt"
java -Djava.awt.headless=true -cp "$CURRENT_CP" \
    savanna.A1aInteractionGeometryCoherenceProbe 300 100 2111 20260629 \
    > "$OUT/INTERACTION_GEOMETRY_300.txt" 2>&1
grep -q 'A1A_INTERACTION_GEOMETRY_COHERENCE PASS' \
    "$OUT/INTERACTION_GEOMETRY_300.txt"

compare_frozen() {
    steps="$1"
    seed="$2"
    key="steps-${steps}-seed-${seed}"
    java -Djava.awt.headless=true -cp "$REFERENCE_CP" \
        savanna.A1aR2AttributionProbe "$steps" 1 "$seed" 20260629 \
        > "$OUT/FROZEN_R2_${key}.txt" 2>&1
    java -Djava.awt.headless=true -cp "$CURRENT_CP" \
        savanna.A1aR2AttributionProbe "$steps" 1 "$seed" 20260629 \
        > "$OUT/D2_CURRENT_${key}.txt" 2>&1
    cmp -s "$OUT/FROZEN_R2_${key}.txt" "$OUT/D2_CURRENT_${key}.txt"
    printf 'PASS frozen-r2 exact attribution steps=%s seed=%s\n' \
        "$steps" "$seed" >> "$OUT/FROZEN_R2_EQUIVALENCE.txt"
}

compare_frozen 300 2111
for seed in 1111 2111 3111 4111 5111
do
    compare_frozen 100 "$seed"
done

java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.A1aR2rD2Probe \
    300 100 2111 20260629 > "$OUT/D2_LEDGER_2111_300.txt" 2>&1
grep -q 'A1A_R2R_D2_PROBE PASS transparent=true' \
    "$OUT/D2_LEDGER_2111_300.txt"
for seed in 1111 2111 3111 4111 5111
do
    java -Djava.awt.headless=true -cp "$CURRENT_CP" savanna.A1aR2rD2Probe \
        100 100 "$seed" 20260629 > "$OUT/D2_LEDGER_${seed}_100.txt" 2>&1
    grep -q 'A1A_R2R_D2_PROBE PASS transparent=true' \
        "$OUT/D2_LEDGER_${seed}_100.txt"
done

shasum -a 256 build/metadata/MAIN_SOURCE_MANIFEST.sha256 \
    "$REFERENCE" "$ROOT/A1A_R3_GATE_SPEC.json" \
    > "$OUT/HASHES.txt"
printf 'A1A_R2R_D2_VERIFICATION PASS\n' > "$OUT/SUMMARY.txt"
printf 'A1a-R2R-D2 verification PASS: %s\n' "$OUT"
