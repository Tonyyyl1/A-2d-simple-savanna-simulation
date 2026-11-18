#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="${1:?usage: test-versioned-ecology-gate-policy.sh <candidate.jar> <test-classes>}"
TEST_CLASSES="${2:?test classes are required}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
PROFILE="$(sh "$ROOT/resolve-current-transparency-profile.sh" "$ROOT")"
PROFILE_NAME="$(basename "$PROFILE")"

if EXPECTED_BALANCE_RATIO=999 "$ROOT/run-versioned-ecology-gate.sh" \
    "$PROFILE" "$JAR" "$TEST_CLASSES" \
    > "$TMP/environment.txt" 2>&1
then
    printf 'FAIL environment override was accepted\n' >&2
    exit 1
fi
grep -q 'promotion gate profiles cannot be overridden' \
    "$TMP/environment.txt"
printf 'PASS environment gate override rejected\n'

cp "$PROFILE" "$TMP/"
cp "$PROFILE.sha256" "$TMP/"
printf '\n# tampered\n' >> "$TMP/$PROFILE_NAME"
if "$ROOT/run-versioned-ecology-gate.sh" \
    "$TMP/$PROFILE_NAME" "$JAR" "$TEST_CLASSES" \
    > "$TMP/profile.txt" 2>&1
then
    printf 'FAIL tampered gate profile was accepted\n' >&2
    exit 1
fi
grep -q 'profile checksum verification failed' "$TMP/profile.txt"
printf 'PASS tampered gate profile rejected\n'

mkdir -p "$TMP/selector/gate-profiles"
cp "$ROOT/gate-profiles/TRANSPARENCY_CURRENT" \
    "$TMP/selector/gate-profiles/"
cp "$PROFILE" "$PROFILE.sha256" "$TMP/selector/gate-profiles/"
SELECTED="$(sh "$ROOT/resolve-current-transparency-profile.sh" \
    "$TMP/selector")"
test "$(basename "$SELECTED")" = "$PROFILE_NAME"
printf 'PASS current transparency selector resolves sealed profile\n'
printf 'transparency-../../escape.properties\n' \
    > "$TMP/selector/gate-profiles/TRANSPARENCY_CURRENT"
if sh "$ROOT/resolve-current-transparency-profile.sh" "$TMP/selector" \
    > "$TMP/selector-unsafe.txt" 2>&1
then
    printf 'FAIL unsafe transparency pointer was accepted\n' >&2
    exit 1
fi
grep -Eq 'unsafe basename|must name transparency' \
    "$TMP/selector-unsafe.txt"
printf 'PASS unsafe transparency pointer rejected\n'
printf 'VERSIONED_GATE_POLICY_FIXTURES PASS: 4/4\n'
