#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
. "$ROOT/runtime-launch-lib.sh"
DIR_INPUT="${1:?usage: run-ecology-candidate.sh <accepted-ecology-candidate-dir> [simulation-args...]}"
shift
case "$DIR_INPUT" in /*) DIR="$DIR_INPUT" ;; *) DIR="$ROOT/$DIR_INPUT" ;; esac
case "$DIR" in "$ROOT"/artifacts/candidates/accepted/*) ;; *) runtime_fail 'ecology candidate must be an accepted sealed artifact' ;; esac
test -d "$DIR" || runtime_fail "ecology candidate is missing: $DIR"
"$ROOT/verify-candidate-seal.sh" "$DIR" >/dev/null
CLASS="$(jq -r '.candidateClass // empty' "$DIR/MANIFEST.json")"
test "$CLASS" = ECOLOGY_CANDIDATE ||
    runtime_fail "candidate is not an ECOLOGY_CANDIDATE: $DIR"
runtime_exec_jar "$CLASS" "$(runtime_require_one_jar "$DIR")" "$@"
