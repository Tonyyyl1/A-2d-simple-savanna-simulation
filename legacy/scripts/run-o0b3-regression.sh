#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CANDIDATE="${1:?usage: run-o0b3-regression.sh <candidate-dir>}"
case "$CANDIDATE" in
    /*) ;;
    *) CANDIDATE="$ROOT/$CANDIDATE" ;;
esac
case "$CANDIDATE" in
    "$ROOT"/artifacts/candidates/accepted/*) ;;
    *) printf 'Candidate must be inside build/candidates: %s\n' "$CANDIDATE" >&2; exit 1 ;;
esac
set -- "$CANDIDATE"/savanna-simulation-*.jar
JAR="$1"
test -f "$JAR" || { printf 'Candidate JAR not found: %s\n' "$JAR" >&2; exit 1; }
mkdir -p "$CANDIDATE/evidence"
java -Djava.awt.headless=true -cp "$JAR" HeatmapSnapshotProbe \
    > "$CANDIDATE/evidence/O0B3_HEATMAP_SNAPSHOT.txt" 2>&1
java -Djava.awt.headless=true -cp "$JAR" HeatmapWindowProbe \
    > "$CANDIDATE/evidence/O0B3_HEATMAP_WINDOWS.txt" 2>&1
java -Djava.awt.headless=true -cp "$JAR" HeatmapSnapshotStressProbe \
    > "$CANDIDATE/evidence/O0B3_HEATMAP_STRESS.txt" 2>&1
grep -q 'Passed [0-9]\+/[0-9]\+ tests' "$CANDIDATE/evidence/O0B3_HEATMAP_SNAPSHOT.txt"
grep -q 'Passed [0-9]\+/[0-9]\+ tests' "$CANDIDATE/evidence/O0B3_HEATMAP_WINDOWS.txt"
grep -q 'O0B3_STRESS PASS' "$CANDIDATE/evidence/O0B3_HEATMAP_STRESS.txt"
printf 'O0b3 heatmap regression PASS: %s\n' "$CANDIDATE"
