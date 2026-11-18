#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
. "$ROOT/runtime-launch-lib.sh"
"$ROOT/validate-machine-state.sh" >/dev/null
BASELINE_REL="$(jq -r '.current.acceptedBaseline.candidate' "$ROOT/DEVELOPMENT_STATUS.json")"
case "$BASELINE_REL" in ''|/*|*'..'*) runtime_fail 'accepted baseline path is unsafe' ;; esac
BASELINE="$ROOT/$BASELINE_REL"
case "$BASELINE" in "$ROOT"/artifacts/candidates/accepted/*) ;; *) runtime_fail 'accepted baseline is outside accepted artifacts' ;; esac
test -d "$BASELINE" || runtime_fail "accepted baseline is missing: $BASELINE"
"$ROOT/verify-candidate-seal.sh" "$BASELINE" >/dev/null
JAR="$(runtime_require_one_jar "$BASELINE")"
POINTER="$ROOT/artifacts/candidates/CURRENT_DEV"
test -f "$POINTER" || runtime_fail 'CURRENT_DEV pointer is missing'
POINTER_JAR="$(awk -F= '$1 == "jar" {print $2}' "$POINTER")"
POINTER_SHA="$(awk -F= '$1 == "sha256" {print $2}' "$POINTER")"
POINTER_BUILD="$(awk -F= '$1 == "build_id" {print $2}' "$POINTER")"
test -n "$POINTER_JAR" && test -n "$POINTER_SHA" && test -n "$POINTER_BUILD" ||
    runtime_fail 'CURRENT_DEV pointer is malformed'
EXPECTED_REL="${JAR#"$ROOT/"}"
test "$POINTER_JAR" = "$EXPECTED_REL" ||
    runtime_fail 'CURRENT_DEV does not identify the routed accepted baseline'
test "$(shasum -a 256 "$JAR" | awk '{print $1}')" = "$POINTER_SHA" ||
    runtime_fail 'CURRENT_DEV SHA does not identify the routed accepted baseline'
test "$(unzip -p "$JAR" META-INF/savanna-build.properties | awk -F= '$1 == "build.id" {print $2}')" = "$POINTER_BUILD" ||
    runtime_fail 'CURRENT_DEV build ID does not identify the routed accepted baseline'
runtime_exec_jar ACCEPTED_BASELINE "$JAR" "$@"
