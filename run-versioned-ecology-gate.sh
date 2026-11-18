#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
PROFILE="${1:?usage: run-versioned-ecology-gate.sh <profile.properties> <candidate.jar> <test-classes> [evidence-file]}"
JAR="${2:?candidate JAR is required}"
TEST_CLASSES="${3:?compiled test classes are required}"
EVIDENCE="${4:-}"

fail() {
    printf 'Versioned ecology gate FAILED: %s\n' "$1" >&2
    exit 1
}

FORBIDDEN_VARIABLES="$(env | awk -F= '
    $1 ~ /^SKIP_/ || $1 ~ /^VERIFY_/ || $1 ~ /^EXPECTED_/ ||
    $1 ~ /^ECOLOGY_GATE_/ { print $1 }
' | LC_ALL=C sort -u)"
if test -n "$FORBIDDEN_VARIABLES"
then
    printf 'Forbidden ecology gate variables:\n%s\n' "$FORBIDDEN_VARIABLES" >&2
    fail 'promotion gate profiles cannot be overridden by the environment'
fi

case "$PROFILE" in /*) ;; *) PROFILE="$ROOT/$PROFILE" ;; esac
case "$JAR" in /*) ;; *) JAR="$ROOT/$JAR" ;; esac
case "$TEST_CLASSES" in /*) ;; *) TEST_CLASSES="$ROOT/$TEST_CLASSES" ;; esac
SIDECAR="$PROFILE.sha256"

test -f "$PROFILE" || fail "profile not found: $PROFILE"
test -f "$SIDECAR" || fail "profile checksum sidecar not found: $SIDECAR"
test -f "$JAR" || fail "candidate JAR not found: $JAR"
test -d "$TEST_CLASSES" || fail "test classes not found: $TEST_CLASSES"

SIDECAR_LINES="$(wc -l < "$SIDECAR" | tr -d ' ')"
test "$SIDECAR_LINES" = 1 || fail 'profile checksum sidecar must contain one line'
EXPECTED_SHA="$(awk '{print $1}' "$SIDECAR")"
EXPECTED_NAME="$(awk '{print $2}' "$SIDECAR")"
test "${#EXPECTED_SHA}" = 64 || fail 'profile checksum must have 64 hex characters'
case "$EXPECTED_SHA" in
    *[!0-9a-f]*) fail 'profile checksum sidecar has an invalid SHA-256' ;;
esac
test "$EXPECTED_NAME" = "$(basename "$PROFILE")" ||
    fail 'profile checksum sidecar must name the profile basename'
if ! (cd "$(dirname "$PROFILE")" && shasum -a 256 -c "$(basename "$SIDECAR")")
then
    fail 'profile checksum verification failed'
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT HUP INT TERM
STATUS=0
cd "$ROOT"
java -Djava.awt.headless=true -cp "$JAR:$TEST_CLASSES" \
    savanna.VersionedEcologyGateRunner "$PROFILE" "$EXPECTED_SHA" \
    > "$TMP" 2>&1 || STATUS=$?
cat "$TMP"
if test -n "$EVIDENCE"
then
    case "$EVIDENCE" in /*) ;; *) EVIDENCE="$ROOT/$EVIDENCE" ;; esac
    test -d "$(dirname "$EVIDENCE")" ||
        fail "evidence directory not found: $(dirname "$EVIDENCE")"
    cp "$TMP" "$EVIDENCE"
fi
exit "$STATUS"
