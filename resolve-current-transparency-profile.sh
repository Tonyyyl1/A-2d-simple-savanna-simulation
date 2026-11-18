#!/usr/bin/env sh
set -eu

ROOT="${1:-$(cd "$(dirname "$0")" && pwd)}"
POINTER="$ROOT/gate-profiles/TRANSPARENCY_CURRENT"

fail() {
    printf 'Transparency profile selection FAILED: %s\n' "$1" >&2
    exit 1
}

test -f "$POINTER" || fail "current-profile pointer not found: $POINTER"
test "$(wc -l < "$POINTER" | tr -d ' ')" = 1 ||
    fail 'current-profile pointer must contain exactly one newline-terminated line'
PROFILE_NAME="$(sed -n '1p' "$POINTER")"
case "$PROFILE_NAME" in
    transparency-*.properties) ;;
    *) fail 'current-profile pointer must name transparency-*.properties' ;;
esac
case "$PROFILE_NAME" in
    *[!A-Za-z0-9._-]*|.*|*/*)
        fail 'current-profile pointer contains an unsafe basename' ;;
esac

PROFILE="$ROOT/gate-profiles/$PROFILE_NAME"
test -f "$PROFILE" || fail "selected profile not found: $PROFILE_NAME"
test -f "$PROFILE.sha256" ||
    fail "selected profile checksum sidecar not found: $PROFILE_NAME.sha256"
printf '%s\n' "$PROFILE"
