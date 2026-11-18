#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
DIR_INPUT="${1:?usage: test-ecology-candidate.sh <accepted-ecology-candidate-dir>}"
case "$DIR_INPUT" in /*) DIR="$DIR_INPUT" ;; *) DIR="$ROOT/$DIR_INPUT" ;; esac
case "$DIR" in "$ROOT"/artifacts/candidates/accepted/*) ;; *)
    printf 'Ecology qualification FAILED: candidate must be under accepted/.\n' >&2
    exit 1 ;;
esac
test -f "$ROOT/ECOLOGY_ACCEPTANCE_V2.json" || {
    printf 'Ecology qualification FAILED: V2 acceptance contract is missing.\n' >&2
    exit 1
}
jq -e '
  .schema == "savanna-ecology-acceptance-v2" and
  .state == "frozen-before-v25-ecology-candidate" and
  .hardGates.officialLongRun.steps == 5000 and
  .hardGates.officialLongRun.finalBalance.limit == 12 and
  .candidateClasses.ECOLOGY_CANDIDATE != null
' "$ROOT/ECOLOGY_ACCEPTANCE_V2.json" >/dev/null || {
    printf 'Ecology qualification FAILED: V2 acceptance contract is malformed.\n' >&2
    exit 1
}
"$ROOT/run-ecology-candidate.sh" "$DIR" --verify-only
JAR="$(set -- "$DIR"/savanna-simulation-*.jar; test "$#" -eq 1; printf '%s\n' "$1")"
ZIP="$(set -- "$DIR"/savanna-simulation-*-source.zip; test "$#" -eq 1; printf '%s\n' "$1")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
unzip -q "$ZIP" -d "$TMP/source"
(
    cd "$TMP/source"
    BUILD_ID="v25-qualification-test" ./test.sh
)
TEST_CLASSES="$TMP/source/build/test-classes"
java -Djava.awt.headless=true -cp "$JAR:$TEST_CLASSES" savanna.AllTests full
java -Djava.awt.headless=true -cp "$JAR:$TEST_CLASSES" savanna.SimulationRunner 5000
printf 'ECOLOGY_CANDIDATE_QUALIFICATION PASS: %s\n' "$DIR"
