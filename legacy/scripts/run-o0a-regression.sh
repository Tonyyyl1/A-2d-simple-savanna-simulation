#!/usr/bin/env sh
set -eu

BATCH_ID="${1:-o0a-water-baseline-20260721}"
TASK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$TASK_ROOT/build/o0a/$BATCH_ID"
TERRAIN_SEED=20260629
if test -e "$OUT"
then
    printf 'O0a evidence directory already exists: %s\n' "$OUT" >&2
    exit 1
fi
mkdir -p "$OUT/calibration-1000" "$OUT/calibration-5000"
java -Djava.awt.headless=true -cp "$TASK_ROOT/build/classes" \
    WaterBehaviorRegressionProbe --compare 1000 1111 "$TERRAIN_SEED" \
    > "$OUT/probe-transparency.txt"
java -Djava.awt.headless=true -cp "$TASK_ROOT/build/classes" \
    WaterBehaviorRegressionProbe --repeat 1000 1111 "$TERRAIN_SEED" \
    > "$OUT/repeatability.txt"
for SEED in 1111 2111 3111 4111 5111
do
    java -Djava.awt.headless=true -cp "$TASK_ROOT/build/classes" \
        WaterBehaviorRegressionProbe 1000 "$SEED" "$TERRAIN_SEED" \
        "$OUT/calibration-1000/$SEED" > "$OUT/calibration-1000/$SEED.txt"
done
for SEED in 1111 2111 3111
do
    java -Djava.awt.headless=true -cp "$TASK_ROOT/build/classes" \
        WaterBehaviorRegressionProbe 5000 "$SEED" "$TERRAIN_SEED" \
        "$OUT/calibration-5000/$SEED" > "$OUT/calibration-5000/$SEED.txt"
done
{
    printf 'O0a calibration PASS\n'
    printf 'calibration_1000=1111,2111,3111,4111,5111\n'
    printf 'calibration_5000=1111,2111,3111\n'
    printf 'validation_frozen_not_run=6111,7111,8111\n'
    printf 's1_holdout_not_run=9111,10111,11111\n'
    printf 'sentinel_not_run=12111,13111\n'
} > "$OUT/O0A_CALIBRATION_ACCEPTANCE.txt"
printf 'O0a regression PASS: %s\n' "$OUT"
