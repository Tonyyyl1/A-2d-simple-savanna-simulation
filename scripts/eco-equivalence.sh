#!/usr/bin/env sh
# Behaviour-equivalence check between two sweep output directories (plan v3.2 A-1).
#   scripts/eco-equivalence.sh <dir-A> <dir-B>
# Runs are paired by seed-steps-thirst (the leading parameter hash is ignored,
# because it changes when parameter keys are added). For every pair, compares the last
# ECO_RESULT line field by field (only the fields of plan appendix A) and
# prints SAME or DIFF <fields>. Exits 0 only when every run is SAME and both
# directories hold the same set of runs.
set -eu
A="${1:?usage: eco-equivalence.sh <dir-A> <dir-B>}"
B="${2:?usage: eco-equivalence.sh <dir-A> <dir-B>}"
FIELDS="seed thirst requested completed final min extinctionStep extinctSpecies balance balanceMean maxOverloadSpan regionLedger birthTrace predatorFunding predatorBirthsKills gatePoint gate balanceNorm"
status=0
count=0
for LOG_A in "$A"/runs/*.log
do
    NAME="$(basename "$LOG_A")"
    KEY="${NAME#*-}"
    LOG_B="$(ls "$B"/runs/*-"$KEY" 2>/dev/null | head -1 || true)"
    if test -z "$LOG_B" || test ! -f "$LOG_B"
    then
        echo "$NAME MISSING in $B"
        status=1
        continue
    fi
    LINE_A="$(grep -h '^ECO_RESULT' "$LOG_A" | tail -1)"
    LINE_B="$(grep -h '^ECO_RESULT' "$LOG_B" | tail -1)"
    if test -z "$LINE_A" || test -z "$LINE_B"
    then
        echo "$NAME NO_RESULT"
        status=1
        continue
    fi
    DIFFS=""
    for FIELD in $FIELDS
    do
        VA="$(printf '%s\n' "$LINE_A" | tr ' ' '\n' | grep "^$FIELD=" | head -1 || true)"
        VB="$(printf '%s\n' "$LINE_B" | tr ' ' '\n' | grep "^$FIELD=" | head -1 || true)"
        test "$VA" = "$VB" || DIFFS="$DIFFS $FIELD"
    done
    count=$((count + 1))
    if test -z "$DIFFS"
    then
        echo "$NAME SAME"
    else
        echo "$NAME DIFF$DIFFS"
        status=1
    fi
done
for LOG_B in "$B"/runs/*.log
do
    KEY="$(basename "$LOG_B")"
    KEY="${KEY#*-}"
    ls "$A"/runs/*-"$KEY" >/dev/null 2>&1 || { echo "$(basename "$LOG_B") MISSING in $A"; status=1; }
done
test "$count" -gt 0 || { echo 'no runs compared' >&2; status=1; }
exit "$status"
