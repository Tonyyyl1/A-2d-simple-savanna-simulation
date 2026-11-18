#!/usr/bin/env sh
# P-0: low-density growth test for the predators (plan phase P, P-0 item 5).
#   scripts/invasion-test.sh <classpath> <output-dir> [cheetah|lion] [--params=key=value,...]
# Starts the chosen predator at a low founding count (Cheetah 15, Lion 25;
# every other species at its default), thirst off, 2000 steps, at the eight
# seeds 1111 2111 11111 12111 21111 22111 23111 24111 (the last four are used
# by this test only). Reports how many seeds end with at least 30 Cheetah
# (Lion: 50). Extra --params are applied on top of the founding count, so a
# candidate setting can be screened the same way. One JVM per run, capped with
# -Xmx$ECO_XMX (default 768m), ECO_JOBS (default 4) in parallel (S3 kernel only; the
# unmerged baseline kernel needs ECO_XMX=1g ECO_JOBS=3).
set -eu
CP="${1:?usage: invasion-test.sh <classpath> <output-dir> [cheetah|lion] [--params=...]}"
OUT="${2:?usage: invasion-test.sh <classpath> <output-dir> [cheetah|lion] [--params=...]}"
SPECIES="${3:-cheetah}"
EXTRA="${4:-}"
case "$SPECIES" in
    cheetah) FOUNDERS=15; THRESHOLD=30; NAME=Cheetah ;;
    lion) FOUNDERS=25; THRESHOLD=50; NAME=Lion ;;
    *) echo "species must be cheetah or lion" >&2; exit 2 ;;
esac
case "$EXTRA" in
    ''|--params=*) ;;
    *) echo "fourth argument must be --params=key=value,..." >&2; exit 2 ;;
esac
EXTRA="${EXTRA#--params=}"
PARAMS="init.$SPECIES=$FOUNDERS${EXTRA:+,$EXTRA}"
export CP OUT PARAMS
export ECO_XMX="${ECO_XMX:-768m}"
mkdir -p "$OUT"
printf '%s\n' 1111 2111 11111 12111 21111 22111 23111 24111 |
    xargs -P "${ECO_JOBS:-4}" -n 1 sh -c '
        LOG="$OUT/run-$0.log"
        java -Xmx"$ECO_XMX" -XX:+UseParallelGC -XX:ParallelGCThreads=1 \
            -Djava.awt.headless=true -cp "$CP" \
            savanna.EcologyMatrixRunner "$0" 2000 off "--params=$PARAMS" > "$LOG" 2>&1 ||
            echo "ECO_RESULT seed=$0 gate=ERROR" >> "$LOG"
    '
{
    echo "invasion species=$NAME founders=$FOUNDERS threshold=$THRESHOLD params=$PARAMS"
    for SEED in 1111 2111 11111 12111 21111 22111 23111 24111
    do
        LINE="$(grep -h '^ECO_RESULT' "$OUT/run-$SEED.log" | tail -1 || true)"
        FINAL="$(printf '%s\n' "$LINE" | tr ' ' '\n' | sed -n 's/^final=//p' | head -1)"
        COUNT="$(printf '%s\n' "$FINAL" | tr ',' '\n' | sed -n "s/^$NAME://p")"
        EXT="$(printf '%s\n' "$LINE" | tr ' ' '\n' | sed -n 's/^extinctionStep=//p' | head -1)"
        echo "seed=$SEED $NAME=${COUNT:-?} extinctionStep=${EXT:-?}"
    done
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
GROWN="$(grep -c "^seed=.* $NAME=[0-9]" "$OUT/summary.txt" || true)"
PASSED="$(awk -v n="$NAME" -v t="$THRESHOLD" -F'[ =]' '/^seed=/ { for(i = 1; i < NF; i++) if($i == n && $(i+1) + 0 >= t) c++ } END { print c + 0 }' "$OUT/summary.txt")"
echo "INVASION $NAME seeds_at_or_above_$THRESHOLD=$PASSED/8 (runs_with_result=$GROWN)" | tee -a "$OUT/summary.txt"
