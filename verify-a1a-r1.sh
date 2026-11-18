#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:?usage: verify-a1a-r1.sh <candidate-dir>}"
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
test -f "$REFERENCE" || {
    printf 'Missing frozen R0 reference JAR: %s\n' "$REFERENCE" >&2
    exit 1
}
EVIDENCE="$OUT/evidence"
mkdir -p "$EVIDENCE"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/savanna-a1a-r1.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

find "$ROOT/src/test/java" -type f -name '*.java' -print | LC_ALL=C sort \
    > "$TMP/test-sources.list"
javac --release 17 -cp "$JAR" -d "$TMP/test-classes" \
    @"$TMP/test-sources.list"
TEST_CLASSPATH="$JAR:$TMP/test-classes"
REFERENCE_CLASSPATH="$REFERENCE:$TMP/test-classes"

java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.PositionKernelTests > "$EVIDENCE/POSITION_KERNEL_FIXTURES.txt" 2>&1
grep -q 'A1A_R1_KERNEL_FIXTURES PASS' "$EVIDENCE/POSITION_KERNEL_FIXTURES.txt"
java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.A1aR1KernelStressProbe > "$EVIDENCE/POSITION_KERNEL_STRESS.txt" 2>&1
grep -q 'A1A_R1_KERNEL_STRESS PASS' "$EVIDENCE/POSITION_KERNEL_STRESS.txt"

java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.A1aR1EcologyFingerprintProbe 1000 \
    > "$EVIDENCE/CANDIDATE_STEP_FINGERPRINTS_1000.txt" 2>&1
java -Djava.awt.headless=true -cp "$REFERENCE_CLASSPATH" \
    savanna.A1aR1EcologyFingerprintProbe 1000 \
    > "$EVIDENCE/R0_REFERENCE_STEP_FINGERPRINTS_1000.txt" 2>&1
diff -u "$EVIDENCE/R0_REFERENCE_STEP_FINGERPRINTS_1000.txt" \
    "$EVIDENCE/CANDIDATE_STEP_FINGERPRINTS_1000.txt" \
    > "$EVIDENCE/STEP_FINGERPRINT_DIFF.txt"

java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
    savanna.PositionCostProbe 1000 > "$EVIDENCE/POSITION_COST_WARMUP.txt" 2>&1
for RUN in 1 2 3
do
    java -Djava.awt.headless=true -cp "$TEST_CLASSPATH" \
        savanna.PositionCostProbe 1000 \
        > "$EVIDENCE/POSITION_COST_RUN_$RUN.txt" 2>&1
done
R0_MEDIAN_NANOS=150918594542
R1_LIMIT_NANOS=158464524269
set -- $(awk -F'elapsedNanos=' '
    /POSITION_COST_BASELINE/ {
        split($2, part, " "); print part[1]
    }' "$EVIDENCE/POSITION_COST_RUN_1.txt" \
       "$EVIDENCE/POSITION_COST_RUN_2.txt" \
       "$EVIDENCE/POSITION_COST_RUN_3.txt" | LC_ALL=C sort -n)
test "$#" -eq 3
R1_MEDIAN_NANOS="$2"
test "$R1_MEDIAN_NANOS" -le "$R1_LIMIT_NANOS"
{
    printf 'A1a-R1 verification PASS\n'
    printf 'reference_r0_median_nanos=%s\n' "$R0_MEDIAN_NANOS"
    printf 'r1_limit_nanos=%s\n' "$R1_LIMIT_NANOS"
    printf 'r1_elapsed_median_nanos=%s\n' "$R1_MEDIAN_NANOS"
    printf 'step_fingerprints=1000_exact_match\n'
    printf 'kernel_fixtures=PASS\n'
    printf 'kernel_stress=PASS\n'
} > "$EVIDENCE/A1A_R1_GATE_SUMMARY.txt"
printf 'A1a-R1 verification PASS: %s\n' "$OUT"
