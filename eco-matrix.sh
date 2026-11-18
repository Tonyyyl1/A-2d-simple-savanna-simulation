#!/usr/bin/env sh
# T3 ecology seed matrix (plan v3.0 §2.4, §7 M3-2).
#   [ECO_SET=default|holdout|procedural] ./eco-matrix.sh <classpath> <output-dir> [--enforce]
# default: 5000 steps at seed 1111 and 1000 steps at seeds 1111..5111;
# holdout: 1000 steps at seeds 6111..10111 (run once, after parameters are
# frozen); procedural: G-ECO-2 (seeds 3111..5111, 1000 steps) on the
# PROCEDURAL_V2 terrain seeds 4201, 9317 and 17689. Every run has thirst off
# and on, in parallel JVMs (ECO_JOBS, default 3; each capped at
# -Xmx$ECO_XMX, default 1g, parallel GC on one thread). Writes one log per run plus
# summary.txt. Without --enforce the gate verdicts are reported
# only; with it, a FAIL in any thirst-off run (or any ERROR) makes the script
# exit 1; thirst-on FAILs are recorded, not enforced (B2).
set -eu

CP="${1:?usage: eco-matrix.sh <classpath> <output-dir> [--enforce]}"
OUT="${2:?usage: eco-matrix.sh <classpath> <output-dir> [--enforce]}"
ENFORCE="${3:-}"
test -z "$ENFORCE" || test "$ENFORCE" = --enforce || {
    echo 'third argument must be --enforce' >&2; exit 2; }
JOBS="${ECO_JOBS:-3}"
ECO_XMX="${ECO_XMX:-1g}"
export ECO_XMX
mkdir -p "$OUT"

RUNS=""
case "${ECO_SET:-default}" in
    default)
        RUNS="1111:5000:off 1111:5000:on"
        for SEED in 1111 2111 3111 4111 5111
        do
            RUNS="$RUNS $SEED:1000:off $SEED:1000:on"
        done ;;
    holdout)
        for SEED in 6111 7111 8111 9111 10111
        do
            RUNS="$RUNS $SEED:1000:off $SEED:1000:on"
        done ;;
    procedural)
        for TERRAIN in 4201 9317 17689
        do
            for SEED in 3111 4111 5111
            do
                RUNS="$RUNS $SEED:1000:off:$TERRAIN $SEED:1000:on:$TERRAIN"
            done
        done ;;
    *) echo "unknown ECO_SET: $ECO_SET" >&2; exit 2 ;;
esac

# Each run spec arrives as $3; macOS xargs -I caps the command at 255 bytes.
printf '%s\n' $RUNS | xargs -P "$JOBS" -n 1 sh -c '
    IFS=: read -r SEED STEPS THIRST TERRAIN <<EOF
$3
EOF
    LOG="$2/run-$SEED-$STEPS-$THIRST${TERRAIN:+-t$TERRAIN}.log"
    java -Xmx"$ECO_XMX" -XX:+UseParallelGC -XX:ParallelGCThreads=1 \
        -Djava.awt.headless=true -cp "$1" \
        savanna.EcologyMatrixRunner "$SEED" "$STEPS" "$THIRST" \
        ${TERRAIN:+--terrain-profile=PROCEDURAL_V2 --terrain-seed=$TERRAIN} > "$LOG" 2>&1 ||
        echo "ECO_RESULT seed=$SEED thirst=$THIRST requested=$STEPS gate=ERROR" >> "$LOG"
' sh "$CP" "$OUT"

{
    printf 'set: %s load: %s\n' "${ECO_SET:-default}" "$(uptime | sed 's/.*load averages*: //')"
    for SPEC in $RUNS
    do
        IFS=: read -r SEED STEPS THIRST TERRAIN <<EOF
$SPEC
EOF
        LOG="$OUT/run-$SEED-$STEPS-$THIRST${TERRAIN:+-t$TERRAIN}.log"
        test -f "$LOG" && grep -h '^ECO_RESULT' "$LOG" | tail -1
    done
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"

# B2 (user decision 2026-10-28): only thirst-off verdicts are enforced; thirst-on
# runs are reported (their gate= field stays in summary.txt) but never fail the
# matrix, except for gate=ERROR (a run that did not finish).
if test "$ENFORCE" = --enforce &&
    { grep 'thirst=off' "$OUT/summary.txt" | grep -q 'gate=\(FAIL\|ERROR\)' ||
      grep -q 'gate=ERROR' "$OUT/summary.txt"; }
then
    echo 'ECO_MATRIX FAIL' >&2
    exit 1
fi
if grep -q 'gate=ERROR' "$OUT/summary.txt"
then
    echo 'ECO_MATRIX ERROR: a run did not finish' >&2
    exit 1
fi
echo 'ECO_MATRIX DONE'
