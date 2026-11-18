#!/usr/bin/env sh
# Test tiers from plan v3.0 §2.4.
#   ./check.sh            T2 (before every commit): one dev compile, every test
#                         group plus the 1000-step integration run in one JVM,
#                         then the display/render bytecode contracts.
#   ./check.sh milestone  T3 (milestone acceptance): T2, then ./test.sh (stamped
#                         build, JAR boundary, AllTests against the JAR,
#                         --verify-only), WaterSafetyProbe 1000 100 and the
#                         ecology seed matrix. ECO_ENFORCE=1 turns the
#                         ecology verdicts into a hard gate (from M3 on).
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="${1:-commit}"
case "$MODE" in
    commit|milestone) ;;
    *) echo 'usage: check.sh [milestone]' >&2; exit 2 ;;
esac
JAVA_OPTS="-Xmx${CHECK_XMX:-2g} -Djava.awt.headless=true -Duser.language=en -Duser.country=US"

"$ROOT/compile-dev.sh"
DEV_CP="$ROOT/build/dev-classes:$ROOT/build/dev-test-classes"
# shellcheck disable=SC2086
java $JAVA_OPTS -cp "$DEV_CP" savanna.AllTests

# The bytecode contracts read main and test classes from one directory.
CONTRACT_CLASSES="$ROOT/build/dev-contract-classes"
rm -rf "$CONTRACT_CLASSES"
mkdir -p "$CONTRACT_CLASSES"
cp -R "$ROOT/build/dev-classes/." "$ROOT/build/dev-test-classes/." "$CONTRACT_CLASSES/"
for CONTRACT in "$ROOT"/scripts/contracts/*.sh
do
    sh "$CONTRACT" "$CONTRACT_CLASSES"
done
"$ROOT/check-web.sh"
echo 'CHECK T2 PASS'
test "$MODE" = milestone || exit 0

OUT="$ROOT/build/milestone-$(date -u '+%Y%m%dT%H%M%SZ')"
mkdir -p "$OUT"
BUILD_ID="${BUILD_ID:-milestone-check}" "$ROOT/test.sh" > "$OUT/test-sh.log" 2>&1 || {
    tail -20 "$OUT/test-sh.log" >&2
    echo "CHECK T3 FAIL: test.sh (log: $OUT/test-sh.log)" >&2
    exit 1
}
FORMAL_CP="$ROOT/build/classes:$ROOT/build/test-classes"
# shellcheck disable=SC2086
java $JAVA_OPTS -cp "$FORMAL_CP" savanna.WaterSafetyProbe 1000 100 \
    > "$OUT/water-safety.log" 2>&1 || {
    tail -20 "$OUT/water-safety.log" >&2
    echo "CHECK T3 FAIL: WaterSafetyProbe (log: $OUT/water-safety.log)" >&2
    exit 1
}
if test "${ECO_ENFORCE:-0}" = 1
then
    "$ROOT/eco-matrix.sh" "$FORMAL_CP" "$OUT/eco-matrix" --enforce
else
    "$ROOT/eco-matrix.sh" "$FORMAL_CP" "$OUT/eco-matrix"
fi
echo "CHECK T3 PASS (evidence: $OUT)"
