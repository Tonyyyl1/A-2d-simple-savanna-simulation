#!/usr/bin/env bash
# usage: scripts/perf/stage.sh <tag> <withAuditOff:0|1>
# The per-commit verification of the performance work: core group, ./check.sh (T2), the five
# reference traces with the audit on and, when requested, with -Dsavanna.audit=off.
# Never run it next to another simulation JVM; it recompiles build/dev-classes.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
TAG="$1"; OFF="${2:-0}"
LOGDIR="$ROOT/build/perf-run"; mkdir -p "$LOGDIR"
echo "== core"; ./test-dev.sh core 2>&1 | grep -E "^FAIL|Passed|GATE_(TOTAL|PASS|FAIL)"
echo "== check"; ./check.sh > "$LOGDIR/$TAG-check.log" 2>&1; echo "CHECK_EXIT=$?"
grep -E "CHECK T2|Passed |GATE_(TOTAL|FAIL)|^FAIL" "$LOGDIR/$TAG-check.log" | head -12
CP=build/dev-classes:build/dev-test-classes
echo "== traces on"; bash scripts/perf/verify-traces.sh "$TAG-on" "$CP" 2>&1 | grep -E "identical|differ|TRACE_RESULT" | sed -e "s#$ROOT/build/##g" | cut -c1-150
if [ "$OFF" = "1" ]; then
  echo "== traces off"; bash scripts/perf/verify-traces.sh "$TAG-off" "$CP" -Dsavanna.audit=off 2>&1 | grep -E "identical|differ|TRACE_RESULT" | sed -e "s#$ROOT/build/##g" | cut -c1-150
fi
echo STAGE_DONE
