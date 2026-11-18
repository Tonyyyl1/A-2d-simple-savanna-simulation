#!/usr/bin/env sh
# Behaviour-equivalence check between two sweep output directories (plan v3.2 A-1).
#   scripts/eco-equivalence.sh <dir-A> <dir-B> [PATTERN [EXPECTED]]
# Runs are paired by seed-steps-thirst (the leading parameter hash is ignored,
# because it changes when parameter keys are added). For every pair, compares the last
# ECO_RESULT line field by field (only the fields of plan appendix A) and
# prints SAME or DIFF <fields>. Exits 0 only when every run is SAME and both
# directories hold the same set of runs.
# Optional PATTERN (shell glob) restricts both directories to the runs whose name,
# without the parameter hash, matches it (for example '*-1000-*' selects the 1000-step
# runs and not the 10000-step ones). With PATTERN a last line "EQUIVALENCE n/m SAME"
# (or "EQUIVALENCE FAIL n/m SAME" when the exit status is non-zero) is printed. Optional EXPECTED is the number of runs that must be compared; any other
# count exits non-zero, which catches a glob that matched too few or too many runs.
# Without PATTERN the behaviour and output are unchanged.
set -eu
A="${1:?usage: eco-equivalence.sh <dir-A> <dir-B> [PATTERN [EXPECTED]]}"
B="${2:?usage: eco-equivalence.sh <dir-A> <dir-B> [PATTERN [EXPECTED]]}"
PATTERN="${3:-}"
EXPECTED="${4:-}"
FIELDS="seed thirst requested completed final min extinctionStep extinctSpecies balance balanceMean maxOverloadSpan regionLedger birthTrace predatorFunding predatorBirthsKills gatePoint gate balanceNorm"
status=0
count=0
same=0
for LOG_A in "$A"/runs/*.log
do
    NAME="$(basename "$LOG_A")"
    KEY="${NAME#*-}"
    if test -n "$PATTERN"
    then
        case "$KEY" in
            $PATTERN) ;;
            *) continue ;;
        esac
    fi
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
        same=$((same + 1))
    else
        echo "$NAME DIFF$DIFFS"
        status=1
    fi
done
for LOG_B in "$B"/runs/*.log
do
    KEY="$(basename "$LOG_B")"
    KEY="${KEY#*-}"
    if test -n "$PATTERN"
    then
        case "$KEY" in
            $PATTERN) ;;
            *) continue ;;
        esac
    fi
    ls "$A"/runs/*-"$KEY" >/dev/null 2>&1 || { echo "$(basename "$LOG_B") MISSING in $A"; status=1; }
done
test "$count" -gt 0 || { echo 'no runs compared' >&2; status=1; }
if test -n "$EXPECTED" && test "$count" -ne "$EXPECTED"
then
    echo "expected $EXPECTED runs but compared $count" >&2
    status=1
fi
if test -n "$PATTERN"
then
    if test "$status" -eq 0
    then
        echo "EQUIVALENCE $same/$count SAME"
    else
        echo "EQUIVALENCE FAIL $same/$count SAME"
    fi
fi
exit "$status"
