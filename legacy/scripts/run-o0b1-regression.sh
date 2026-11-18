#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CANDIDATE="${1:?usage: run-o0b1-regression.sh <candidate-dir>}"
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
java -Djava.awt.headless=true -cp "$JAR" FrameEventSummaryProbe \
    > "$CANDIDATE/evidence/O0B1_FRAME_EVENT_SUMMARY.txt" 2>&1
grep -q 'O0B1_FRAME_EVENT_SUMMARY PASS' \
    "$CANDIDATE/evidence/O0B1_FRAME_EVENT_SUMMARY.txt"
printf 'O0b1 frame-event regression PASS: %s\n' "$CANDIDATE"
