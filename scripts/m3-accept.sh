#!/usr/bin/env sh
# M3-2 acceptance (plan v3.0 §7), one heavy job at a time:
#   1. ECO_ENFORCE=1 ./check.sh milestone   (G-ECO-1..5 as hard gates)
#   2. only if 1 passed: the holdout seeds 6111-10111, run once and
#      reported, never used for tuning (ECO_SET=holdout)
#   3. only if 1 passed: G-ECO-2 on PROCEDURAL_V2 terrain seeds 4201, 9317,
#      17689 (ECO_SET=procedural, enforced)
# 2 and 3 use the stamped build that step 1 produced and verified.
#   scripts/m3-accept.sh
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/m3-accept-$(date -u '+%Y%m%dT%H%M%SZ')"
mkdir -p "$OUT"
git -C "$ROOT" rev-parse HEAD > "$OUT/commit.txt"
ECO_ENFORCE=1 "$ROOT/check.sh" milestone > "$OUT/t3.log" 2>&1
T3=$?
HOLDOUT=skipped
PROCEDURAL=skipped
if test "$T3" = 0
then
    FORMAL_CP="$ROOT/build/classes:$ROOT/build/test-classes"
    ECO_SET=holdout "$ROOT/eco-matrix.sh" "$FORMAL_CP" "$OUT/holdout" \
        > "$OUT/holdout.log" 2>&1
    HOLDOUT=$?
    ECO_SET=procedural "$ROOT/eco-matrix.sh" "$FORMAL_CP" "$OUT/procedural" --enforce \
        > "$OUT/procedural.log" 2>&1
    PROCEDURAL=$?
fi
echo "M3_ACCEPT t3=$T3 holdout=$HOLDOUT procedural=$PROCEDURAL out=$OUT" | tee "$OUT/result.txt"
