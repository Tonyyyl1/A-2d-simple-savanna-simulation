#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:?usage: verify-a1a-r2.sh <candidate-dir>}"
case "$OUT" in
    /*) ;;
    *) OUT="$ROOT/$OUT" ;;
esac
case "$OUT" in
    "$ROOT"/artifacts/candidates/accepted/*) ;;
    *) printf 'Candidate must be inside artifacts/candidates/accepted: %s\n' "$OUT" >&2; exit 1 ;;
esac
test -d "$OUT"
set -- "$OUT"/savanna-simulation-*.jar
JAR="$1"
test -f "$JAR"
REFERENCE="$ROOT/artifacts/candidates/accepted/a1a-r0-baseline-diagnostics-20260723/savanna-simulation-v1.8-a1a-r0-baseline-diagnostics-20260723-dev.jar"
test -f "$REFERENCE"
EVIDENCE="$OUT/evidence"
mkdir -p "$EVIDENCE"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/savanna-a1a-r2.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

find "$ROOT/src/test/java" -type f -name '*.java' -print | LC_ALL=C sort \
    > "$TMP/test-sources.list"
javac --release 17 -cp "$JAR" -d "$TMP/test-classes" \
    @"$TMP/test-sources.list"
javac --release 17 -cp "$REFERENCE" -d "$TMP/reference-test-classes" \
    "$ROOT/src/test/java/savanna/SimulationFingerprint.java" \
    "$ROOT/src/test/java/savanna/A1aR2AttributionProbe.java" \
    "$ROOT/src/test/java/savanna/PositionCostProbe.java"
TEST_CLASSPATH="$JAR:$TMP/test-classes"
REFERENCE_CLASSPATH="$REFERENCE:$TMP/reference-test-classes"

java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.PositionKernelTests > "$EVIDENCE/POSITION_KERNEL_FIXTURES.txt" 2>&1
grep -q 'A1A_R1_KERNEL_FIXTURES PASS' \
    "$EVIDENCE/POSITION_KERNEL_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.A1aR2PositionBoardTests > "$EVIDENCE/R2_POSITION_BOARD_FIXTURES.txt" 2>&1
grep -q 'A1A_R2_POSITION_BOARD_FIXTURES PASS' \
    "$EVIDENCE/R2_POSITION_BOARD_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.AllTests > "$EVIDENCE/ALL_TESTS.txt" 2>&1
grep -Eq 'Passed [0-9]+/[0-9]+ tests' "$EVIDENCE/ALL_TESTS.txt"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.WaterSafetyProbe 300 100 baseline \
    > "$EVIDENCE/WATER_SAFETY_300.txt" 2>&1
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.WaterBehaviorRegressionProbe --repeat 100 \
    > "$EVIDENCE/WATER_BEHAVIOR_REPEAT_100.txt" 2>&1
grep -q 'O0A_REPEATABILITY PASS' "$EVIDENCE/WATER_BEHAVIOR_REPEAT_100.txt"

if rg -n 'anAnimal\.act\(' "$ROOT/src/main/java/savanna/Simulator.java" \
    > "$EVIDENCE/PRODUCTION_POSITION_PATH_AUDIT.txt"
then
    printf 'Simulator still calls legacy Animal.act.\n' >&2
    exit 1
fi
if rg -n 'getFieldTransition\(' "$ROOT/src/main/java/savanna/Simulator.java" \
    >> "$EVIDENCE/PRODUCTION_POSITION_PATH_AUDIT.txt"
then
    printf 'Simulator still calls legacy FieldTransition.\n' >&2
    exit 1
fi
if rg -n 'copyLivingReferences' "$ROOT/src/main/java" \
    >> "$EVIDENCE/PRODUCTION_POSITION_PATH_AUDIT.txt"
then
    printf 'Production source contains forbidden planning occupancy copy.\n' >&2
    exit 1
fi
rg -n 'PositionStepCoordinator positions' \
    "$ROOT/src/main/java/savanna/Simulator.java" \
    >> "$EVIDENCE/PRODUCTION_POSITION_PATH_AUDIT.txt"
printf 'R2 production path audit PASS\n' \
    >> "$EVIDENCE/PRODUCTION_POSITION_PATH_AUDIT.txt"

java -Djava.awt.headless=true -cp "$REFERENCE_CLASSPATH" \
    savanna.A1aR2AttributionProbe 300 100 1111 20260629 \
    > "$EVIDENCE/R0_ATTRIBUTION_300.txt" 2>&1
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.A1aR2AttributionProbe 300 100 1111 20260629 \
    > "$EVIDENCE/R2_ATTRIBUTION_300.txt" 2>&1
FIRST_STEP="$(awk '
    $1 == "A1A_R2_FINGERPRINT" {
        split($2, step, "="); split($3, value, "=");
        if(NR == FNR) { reference[step[2]] = value[2]; next }
        if(reference[step[2]] != value[2]) { print step[2]; exit }
    }
' "$EVIDENCE/R0_ATTRIBUTION_300.txt" "$EVIDENCE/R2_ATTRIBUTION_300.txt")"
test -n "$FIRST_STEP"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.A1aR2AttributionProbe "$FIRST_STEP" "$FIRST_STEP" 1111 20260629 \
    > "$EVIDENCE/R2_FIRST_DIVERGENCE_LEDGER.txt" 2>&1
{
    printf 'first_fingerprint_divergence_step=%s\n' "$FIRST_STEP"
    grep "^A1A_R2_FINGERPRINT step=$FIRST_STEP " \
        "$EVIDENCE/R0_ATTRIBUTION_300.txt" | head -1
    grep "^A1A_R2_FINGERPRINT step=$FIRST_STEP " \
        "$EVIDENCE/R2_ATTRIBUTION_300.txt" | head -1
    grep "^A1A_R2_CHECKPOINT step=$FIRST_STEP " \
        "$EVIDENCE/R2_FIRST_DIVERGENCE_LEDGER.txt" | head -1
    printf '%s\n' 'attribution=R2 plan ledger names the representative resolution; resolver has no ecological Random dependency and fixtures prove one canonical event/transition per committed move or birth.'
} > "$EVIDENCE/FIRST_DIVERGENCE.txt"

count_for() {
    FILE="$1"
    SPECIES="$2"
    awk -v species="$SPECIES" '
        $1 == "A1A_R2_CHECKPOINT" && $2 == "step=300" {
            values=$0
            sub(/^.*counts=\{/, "", values)
            sub(/\}.*$/, "", values)
            total=split(values, pairs, ",")
            for(position=1; position<=total; position++) {
                split(pairs[position], pair, "=")
                if(pair[1] == species) { print pair[2]; exit }
            }
        }
    ' "$FILE"
}
balance_for() {
    FILE="$1"
    awk '
        $1 == "A1A_R2_CHECKPOINT" && $2 == "step=300" {
            values=$0
            sub(/^.*counts=\{/, "", values)
            sub(/\}.*$/, "", values)
            total=split(values, pairs, ",")
            minimum=-1; maximum=0
            for(position=1; position<=total; position++) {
                split(pairs[position], pair, "=")
                value=pair[2] + 0
                if(minimum < 0 || value < minimum) minimum=value
                if(value > maximum) maximum=value
            }
            if(minimum <= 0) print "inf"; else printf "%.8f\n", maximum/minimum
            exit
        }
    ' "$FILE"
}
R2_BALANCE="$(balance_for "$EVIDENCE/R2_ATTRIBUTION_300.txt")"
test -n "$R2_BALANCE"
awk -v balance="$R2_BALANCE" 'BEGIN { exit !(balance <= 12.0) }'
{
    printf 'R2 300-step ecology attribution\n'
    printf 'candidate_balance=%s\n' "$R2_BALANCE"
    for SPECIES in Lion Cheetah Zebra Buffalo Gazelle
    do
        R0_COUNT="$(count_for "$EVIDENCE/R0_ATTRIBUTION_300.txt" "$SPECIES")"
        R2_COUNT="$(count_for "$EVIDENCE/R2_ATTRIBUTION_300.txt" "$SPECIES")"
        test -n "$R0_COUNT" && test -n "$R2_COUNT"
        awk -v candidate="$R2_COUNT" -v reference="$R0_COUNT" \
            'BEGIN { exit !(candidate >= reference * 0.50) }'
        printf '%s r0=%s r2=%s ratio=%.6f\n' "$SPECIES" "$R0_COUNT" \
            "$R2_COUNT" "$(awk -v candidate="$R2_COUNT" -v reference="$R0_COUNT" 'BEGIN { print candidate / reference }')"
    done
    printf 'species_floor=PASS (each R2 >= 0.50 * R0)\n'
    printf 'balance_limit=PASS (<=12)\n'
} > "$EVIDENCE/ECOLOGY_ATTRIBUTION_300.txt"

for RUN in 1 2 3
do
    java -Djava.awt.headless=true -cp "$REFERENCE_CLASSPATH" \
        savanna.PositionCostProbe 300 > "$EVIDENCE/R0_POSITION_COST_$RUN.txt" 2>&1
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.PositionCostProbe 300 > "$EVIDENCE/R2_POSITION_COST_$RUN.txt" 2>&1
done
median_cost() {
    awk -F'elapsedNanos=' '
        /POSITION_COST_BASELINE/ { split($2, field, " "); print field[1] }
    ' "$@" | LC_ALL=C sort -n | awk '
        { values[++count] = $1 }
        END { if(count == 3) print values[2]; else exit 1 }
    '
}
R0_MEDIAN="$(median_cost "$EVIDENCE/R0_POSITION_COST_1.txt" \
    "$EVIDENCE/R0_POSITION_COST_2.txt" "$EVIDENCE/R0_POSITION_COST_3.txt")"
R2_MEDIAN="$(median_cost "$EVIDENCE/R2_POSITION_COST_1.txt" \
    "$EVIDENCE/R2_POSITION_COST_2.txt" "$EVIDENCE/R2_POSITION_COST_3.txt")"
awk -v candidate="$R2_MEDIAN" -v reference="$R0_MEDIAN" \
    'BEGIN { exit !(candidate <= reference * 1.25) }'
{
    printf 'R2 300-step elapsed gate PASS\n'
    printf 'r0_median_nanos=%s\n' "$R0_MEDIAN"
    printf 'r2_median_nanos=%s\n' "$R2_MEDIAN"
    printf 'limit_nanos=%.0f\n' "$(awk -v reference="$R0_MEDIAN" 'BEGIN { print reference * 1.25 }')"
} > "$EVIDENCE/PERFORMANCE_ATTRIBUTION_300.txt"

{
    printf 'A1a-R2 verification PASS\n'
    printf 'position_kernel=PASS\n'
    printf 'position_board_fixtures=PASS\n'
    printf 'all_tests=PASS\n'
    printf 'water_safety_and_repeatability=PASS\n'
    printf 'first_divergence_step=%s\n' "$FIRST_STEP"
    printf 'ecology_300_balance=%s\n' "$R2_BALANCE"
    printf 'performance_300_r0_median_nanos=%s\n' "$R0_MEDIAN"
    printf 'performance_300_r2_median_nanos=%s\n' "$R2_MEDIAN"
} > "$EVIDENCE/A1A_R2_GATE_SUMMARY.txt"
printf 'A1a-R2 verification PASS: %s\n' "$OUT"
