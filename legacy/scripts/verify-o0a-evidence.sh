#!/usr/bin/env sh
set -eu

TASK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:?usage: verify-o0a-evidence.sh <o0a-evidence-directory>}"
case "$OUT" in
    /*) ;;
    *) OUT="$TASK_ROOT/$OUT" ;;
esac
test -d "$OUT" || { printf 'O0a evidence directory not found: %s\n' "$OUT" >&2; exit 1; }
grep -q 'O0A_PROBE_TRANSPARENCY PASS steps=1000' "$OUT/probe-transparency.txt"
grep -q 'O0A_REPEATABILITY PASS steps=1000' "$OUT/repeatability.txt"
for SEED in 1111 2111 3111 4111 5111
do
    REPORT="$OUT/calibration-1000/$SEED/water-behavior-regression.txt"
    test -f "$REPORT"
    grep -q 'IDENTITY startMissing=0 birthMismatch=0 transitionUnknown=0' "$REPORT"
    grep -q 'COMPLETED step=1000' "$REPORT"
done
for SEED in 1111 2111 3111
do
    REPORT="$OUT/calibration-5000/$SEED/water-behavior-regression.txt"
    test -f "$REPORT"
    grep -q 'IDENTITY startMissing=0 birthMismatch=0 transitionUnknown=0' "$REPORT"
    grep -q 'COMPLETED step=5000' "$REPORT"
done
test ! -e "$OUT/validation"
test ! -e "$OUT/final-holdout"
test ! -e "$OUT/sentinel"
printf 'O0a evidence verification PASS: %s\n' "$OUT"
